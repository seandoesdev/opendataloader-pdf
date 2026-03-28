/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.opendataloader.pdf.paper.ZoneType;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a page's list of IObject content into zones based on visual boundaries
 * such as vertical gaps, font size changes, and heading elements.
 */
public class ZoneDetector {

    private static final double PAGE_HEIGHT = 792.0;
    private static final double GAP_MULTIPLIER = 2.0;
    private static final double FONT_SIZE_DIFF_THRESHOLD = 2.0;
    private static final double CENTER_TOLERANCE = 0.15;
    private static final double BOLD_WEIGHT_THRESHOLD = 700.0;
    private static final double BOLD_RATIO_THRESHOLD = 0.5;

    private static final Pattern NUMBER_PREFIX_PATTERN = Pattern.compile("^\\s*\\[?\\d+[.\\])\\s]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.\\-]+@[\\w.\\-]+\\.\\w+");
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");
    private static final String SUPERSCRIPT_CHARS = "\u00B9\u00B2\u00B3\u2070\u2071\u2072\u2073\u2074\u2075\u2076\u2077\u2078\u2079*\u2020\u2021";

    /**
     * Detects and splits page content into zones based on visual boundaries.
     *
     * @param contents       the list of content objects on the page
     * @param pageIndex      zero-based page index
     * @param totalPages     total number of pages in the document
     * @param avgBodyFontSize average body font size for the document
     * @return list of detected zones
     */
    public static List<Zone> detect(List<IObject> contents, int pageIndex, int totalPages, double avgBodyFontSize) {
        List<Zone> zones = new ArrayList<>();
        if (contents == null || contents.isEmpty()) {
            return zones;
        }

        List<IObject> currentZoneObjects = new ArrayList<>();
        BoundingBox prevBounds = null;
        double prevFontSize = -1;

        for (IObject obj : contents) {
            boolean forceNewZone = false;

            // Rule 1: SemanticHeading forces a new zone
            if (obj instanceof SemanticHeading) {
                forceNewZone = true;
            }

            BoundingBox currentBounds = getBoundingBox(obj);
            double currentFontSize = getAvgFontSize(obj);

            if (!forceNewZone && prevBounds != null && currentBounds != null) {
                // Rule 2: Vertical gap > avgBodyFontSize * 2.0
                double verticalGap = prevBounds.getBottomY() - currentBounds.getTopY();
                if (Math.abs(verticalGap) > avgBodyFontSize * GAP_MULTIPLIER) {
                    forceNewZone = true;
                }

                // Rule 3: Font size difference > 2.0pt
                if (!forceNewZone && prevFontSize > 0 && currentFontSize > 0) {
                    if (Math.abs(prevFontSize - currentFontSize) > FONT_SIZE_DIFF_THRESHOLD) {
                        forceNewZone = true;
                    }
                }
            }

            if (forceNewZone && !currentZoneObjects.isEmpty()) {
                zones.add(buildZone(currentZoneObjects, pageIndex, totalPages, avgBodyFontSize));
                currentZoneObjects = new ArrayList<>();
            }

            currentZoneObjects.add(obj);
            if (currentBounds != null) {
                prevBounds = currentBounds;
            }
            if (currentFontSize > 0) {
                prevFontSize = currentFontSize;
            }
        }

        // Add the last zone
        if (!currentZoneObjects.isEmpty()) {
            zones.add(buildZone(currentZoneObjects, pageIndex, totalPages, avgBodyFontSize));
        }

        return zones;
    }

    private static Zone buildZone(List<IObject> objects, int pageIndex, int totalPages, double avgBodyFontSize) {
        BoundingBox bounds = computeBounds(objects);
        ZoneFeatures features = extractFeatures(objects, bounds, pageIndex, totalPages, avgBodyFontSize);
        return new Zone(pageIndex, bounds, new ArrayList<>(objects), features);
    }

    private static BoundingBox computeBounds(List<IObject> objects) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        int pageNumber = 0;

        for (IObject obj : objects) {
            BoundingBox bb = getBoundingBox(obj);
            if (bb != null) {
                pageNumber = bb.getPageNumber();
                minX = Math.min(minX, bb.getLeftX());
                minY = Math.min(minY, bb.getBottomY());
                maxX = Math.max(maxX, bb.getRightX());
                maxY = Math.max(maxY, bb.getTopY());
            }
        }

        if (minX == Double.MAX_VALUE) {
            return new BoundingBox(pageNumber, 0, 0, 0, 0);
        }
        return new BoundingBox(pageNumber, minX, minY, maxX, maxY);
    }

    private static BoundingBox getBoundingBox(IObject obj) {
        if (obj instanceof INode) {
            return ((INode) obj).getBoundingBox();
        }
        if (obj instanceof SemanticTextNode) {
            return ((SemanticTextNode) obj).getBoundingBox();
        }
        return null;
    }

    private static ZoneFeatures extractFeatures(List<IObject> objects, BoundingBox bounds,
                                                 int pageIndex, int totalPages, double avgBodyFontSize) {
        List<SemanticTextNode> textNodes = collectTextNodes(objects);
        String zoneText = buildZoneText(textNodes);

        double relativeY = 1.0 - (bounds.getTopY() / PAGE_HEIGHT);
        double avgFontSize = computeAvgFontSize(textNodes);
        double maxFontSize = computeMaxFontSize(textNodes);
        double fontSizeRatio = avgBodyFontSize > 0 ? avgFontSize / avgBodyFontSize : 1.0;
        boolean isBold = computeIsBold(textNodes);
        boolean isCentered = computeIsCentered(textNodes, bounds);
        boolean isItalic = computeIsItalic(textNodes);
        double textDensity = computeTextDensity(zoneText, bounds);
        int lineCount = textNodes.size();
        double avgLineLength = lineCount > 0 ? (double) zoneText.length() / lineCount : 0.0;
        boolean hasNumberPrefix = NUMBER_PREFIX_PATTERN.matcher(zoneText).find();
        boolean hasEmailPattern = EMAIL_PATTERN.matcher(zoneText).find();
        boolean hasDoiPattern = DOI_PATTERN.matcher(zoneText).find();
        boolean hasSuperscript = containsSuperscript(zoneText);
        double pageRatio = totalPages > 0 ? (double) pageIndex / totalPages : 0.0;
        boolean isHeading = objects.size() == 1 && objects.get(0) instanceof SemanticHeading;

        return new ZoneFeatures(relativeY, avgFontSize, maxFontSize, fontSizeRatio,
                isBold, isCentered, isItalic, textDensity, lineCount, avgLineLength,
                hasNumberPrefix, hasEmailPattern, hasDoiPattern, hasSuperscript,
                pageIndex, pageRatio, isHeading);
    }

    private static List<SemanticTextNode> collectTextNodes(List<IObject> objects) {
        List<SemanticTextNode> textNodes = new ArrayList<>();
        for (IObject obj : objects) {
            collectTextNodesRecursive(obj, textNodes);
        }
        return textNodes;
    }

    private static void collectTextNodesRecursive(IObject obj, List<SemanticTextNode> textNodes) {
        if (obj instanceof SemanticTextNode) {
            textNodes.add((SemanticTextNode) obj);
        } else if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                collectTextNodesRecursive(child, textNodes);
            }
        }
    }

    private static String buildZoneText(List<SemanticTextNode> textNodes) {
        StringBuilder sb = new StringBuilder();
        for (SemanticTextNode node : textNodes) {
            String value = node.getValue();
            if (value != null && !value.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private static double getAvgFontSize(IObject obj) {
        List<SemanticTextNode> nodes = new ArrayList<>();
        collectTextNodesRecursive(obj, nodes);
        return computeAvgFontSize(nodes);
    }

    private static double computeAvgFontSize(List<SemanticTextNode> textNodes) {
        if (textNodes.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (SemanticTextNode node : textNodes) {
            sum += node.getFontSize();
        }
        return sum / textNodes.size();
    }

    private static double computeMaxFontSize(List<SemanticTextNode> textNodes) {
        double max = 0.0;
        for (SemanticTextNode node : textNodes) {
            max = Math.max(max, node.getFontSize());
        }
        return max;
    }

    private static boolean computeIsBold(List<SemanticTextNode> textNodes) {
        if (textNodes.isEmpty()) {
            return false;
        }
        int boldCount = 0;
        for (SemanticTextNode node : textNodes) {
            if (node.getFontWeight() >= BOLD_WEIGHT_THRESHOLD) {
                boldCount++;
            }
        }
        return (double) boldCount / textNodes.size() > BOLD_RATIO_THRESHOLD;
    }

    private static boolean computeIsCentered(List<SemanticTextNode> textNodes, BoundingBox zoneBounds) {
        if (textNodes.isEmpty() || zoneBounds == null) {
            return false;
        }
        double zoneCenter = (zoneBounds.getLeftX() + zoneBounds.getRightX()) / 2.0;
        double zoneWidth = zoneBounds.getRightX() - zoneBounds.getLeftX();
        if (zoneWidth <= 0) {
            return false;
        }
        for (SemanticTextNode node : textNodes) {
            BoundingBox bb = node.getBoundingBox();
            if (bb != null) {
                double textMidpoint = (bb.getLeftX() + bb.getRightX()) / 2.0;
                if (Math.abs(textMidpoint - zoneCenter) / zoneWidth > CENTER_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean computeIsItalic(List<SemanticTextNode> textNodes) {
        for (SemanticTextNode node : textNodes) {
            String fontName = node.getFontName();
            if (fontName != null && (fontName.contains("Italic") || fontName.contains("Oblique"))) {
                return true;
            }
        }
        return false;
    }

    private static double computeTextDensity(String text, BoundingBox bounds) {
        if (bounds == null) {
            return 0.0;
        }
        double width = bounds.getRightX() - bounds.getLeftX();
        double height = bounds.getTopY() - bounds.getBottomY();
        double area = width * height;
        if (area <= 0) {
            return 0.0;
        }
        return text.length() / area;
    }

    private static boolean containsSuperscript(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (SUPERSCRIPT_CHARS.indexOf(text.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
