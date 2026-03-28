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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/**
 * Analyzes a PDF's structure and auto-generates a journal template.
 *
 * Usage: java -jar cli.jar --paper-analyze paper.pdf
 * Output: prints analysis to stdout + saves template JSON to --paper-template-dir or current dir
 */
public class PaperAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(PaperAnalyzer.class.getName());
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.(\\d{4,9})/[^\\s,;}\\]]+");
    private static final Pattern DOI_PREFIX_PATTERN = Pattern.compile("10\\.(\\d{4,9})/([^.\\s]+)");
    private static final Pattern ISSN_PATTERN = Pattern.compile("(?:e?ISSN)[:\\s]*([\\d]{4}-[\\d]{3}[\\dXx])");
    private static final Pattern ABSTRACT_LABELS = Pattern.compile(
        "^\\s*(Abstract|ABSTRACT|Purpose|Background|Objectives|\uCD08\uB85D|\uC694\uC57D|\uAD6D\uBB38\\s*\uCD08\uB85D)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEYWORD_LABELS = Pattern.compile(
        "^\\s*(Keywords|Key\\s*words|KEYWORDS|\uD0A4\uC6CC\uB4DC|\uD575\uC2EC\uC5B4|\uC8FC\uC81C\uC5B4|\uC8FC\uC694\uC5B4)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REF_LABELS = Pattern.compile(
        "^\\s*(References|REFERENCES|\uCC38\uACE0\uBB38\uD5CC|\uCC38\uACE0\\s*\uBB38\uD5CC|Bibliography)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(Received|Revised|Accepted|Published|Submitted|\uD22C\uACE0|\uC2EC\uC0AC|\uAC8C\uC7AC)", Pattern.CASE_INSENSITIVE);

    /**
     * Analyzes a PDF's extracted content and generates a journal template JSON.
     *
     * @param inputPdfName the path to the input PDF file
     * @param contents     page-indexed IObject lists from the base pipeline
     * @param outputDir    directory to write template files (null = current dir)
     * @throws IOException if unable to write output files
     */
    public static void analyze(String inputPdfName, List<List<IObject>> contents, String outputDir) throws IOException {
        // Flatten first 2 pages into elements
        List<ElementInfo> elements = new ArrayList<>();
        Map<Integer, Integer> fontCounts = new HashMap<>();

        for (int pageIdx = 0; pageIdx < contents.size(); pageIdx++) {
            int pageNum = pageIdx + 1;
            for (IObject obj : contents.get(pageIdx)) {
                ElementInfo info = extractInfo(obj, pageNum);
                if (info != null) {
                    elements.add(info);
                    if (!info.isHeading && info.fontSize > 0) {
                        int key = (int) (info.fontSize * 10);
                        fontCounts.merge(key, info.text.length(), Integer::sum);
                    }
                }
            }
        }

        // Body font size = most common
        int bestKey = 100;
        int bestCount = 0;
        for (var e : fontCounts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestKey = e.getKey();
            }
        }
        double bodyFontSize = bestKey / 10.0;

        // === Analyze Structure ===
        String fullFirstPageText = elements.stream()
            .filter(e -> e.page == 1)
            .map(e -> e.text)
            .reduce("", (a, b) -> a + "\n" + b);

        // DOI
        String doi = null;
        String doiPrefix = null;
        String journalId = "unknown";
        Matcher doiMatcher = DOI_PATTERN.matcher(fullFirstPageText);
        if (doiMatcher.find()) {
            doi = doiMatcher.group();
            Matcher prefixMatcher = DOI_PREFIX_PATTERN.matcher(doi);
            if (prefixMatcher.find()) {
                doiPrefix = "10." + prefixMatcher.group(1) + "/" + prefixMatcher.group(2);
                journalId = prefixMatcher.group(2).replaceAll("[^a-zA-Z]", "").toLowerCase();
            }
        }

        // ISSN
        String issn = null;
        Matcher issnMatcher = ISSN_PATTERN.matcher(fullFirstPageText);
        if (issnMatcher.find()) {
            issn = issnMatcher.group(1);
        }

        // Title: largest font heading/element on page 1
        ElementInfo titleElement = null;
        double maxFont = 0;
        for (ElementInfo e : elements) {
            if (e.page > 1) break;
            if (e.fontSize > maxFont && e.text.length() >= 5 && e.fontSize > bodyFontSize * 1.2) {
                maxFont = e.fontSize;
                titleElement = e;
            }
        }

        // Abstract label
        String abstractLabel = null;
        for (ElementInfo e : elements) {
            Matcher m = ABSTRACT_LABELS.matcher(e.text);
            if (m.find()) {
                abstractLabel = m.group(1);
                break;
            }
        }

        // Keyword label
        String keywordLabel = null;
        for (ElementInfo e : elements) {
            Matcher m = KEYWORD_LABELS.matcher(e.text);
            if (m.find()) {
                keywordLabel = m.group(1);
                break;
            }
        }

        // Reference heading
        String refHeading = null;
        for (ElementInfo e : elements) {
            if (REF_LABELS.matcher(e.text).find()) {
                refHeading = e.text.trim();
                break;
            }
        }

        // Skip labels (category labels before title)
        List<String> skipLabels = new ArrayList<>();
        if (titleElement != null) {
            for (ElementInfo e : elements) {
                if (e == titleElement) break;
                if (e.page == 1 && e.text.length() < 30 && e.fontSize < titleElement.fontSize) {
                    String t = e.text.trim();
                    if (!t.isEmpty() && !t.startsWith("http") && !t.startsWith("\u00A9")
                        && !DOI_PATTERN.matcher(t).find() && !ISSN_PATTERN.matcher(t).find()) {
                        skipLabels.add(t);
                    }
                }
            }
        }

        // Exclude patterns (date lines between title and abstract)
        List<String> excludePatterns = new ArrayList<>();
        if (titleElement != null) {
            for (ElementInfo e : elements) {
                if (e.page > 1) break;
                if (DATE_PATTERN.matcher(e.text).find()) {
                    Matcher dm = DATE_PATTERN.matcher(e.text);
                    while (dm.find()) {
                        String pat = dm.group(1) + ":";
                        if (!excludePatterns.contains(pat)) {
                            excludePatterns.add(pat);
                        }
                    }
                }
            }
        }
        if (excludePatterns.isEmpty()) {
            excludePatterns.addAll(List.of("Received:", "Revised:", "Accepted:", "Published:"));
        }

        // Reference entry pattern
        String entryPattern = "bracket_number";
        // Check last pages for [1] vs 1. pattern
        String lastPagesText = elements.stream()
            .filter(e -> e.page > contents.size() * 0.7)
            .map(e -> e.text)
            .reduce("", (a, b) -> a + "\n" + b);
        if (Pattern.compile("\\[\\d+\\]").matcher(lastPagesText).find()) {
            entryPattern = "bracket_number";
        } else if (Pattern.compile("(?m)^\\d+\\.\\s").matcher(lastPagesText).find()) {
            entryPattern = "dot_number";
        }

        // === Print Analysis ===
        System.out.println("=== Paper Structure Analysis ===");
        System.out.println("File: " + new File(inputPdfName).getName());
        System.out.println("Pages: " + contents.size());
        System.out.println("Body font size: " + bodyFontSize);
        System.out.println();
        System.out.println("Journal ID: " + journalId);
        if (doi != null) System.out.println("DOI: " + doi);
        if (doiPrefix != null) System.out.println("DOI prefix: " + doiPrefix);
        if (issn != null) System.out.println("ISSN: " + issn);
        System.out.println();
        if (titleElement != null) {
            System.out.println("Title: \"" + titleElement.text.substring(0, Math.min(60, titleElement.text.length())) + "...\"");
            System.out.println("  page=" + titleElement.page + " fontSize=" + titleElement.fontSize + " heading=" + titleElement.isHeading);
        }
        if (!skipLabels.isEmpty()) System.out.println("Skip labels: " + skipLabels);
        if (!excludePatterns.isEmpty()) System.out.println("Exclude patterns: " + excludePatterns);
        if (abstractLabel != null) System.out.println("Abstract label: \"" + abstractLabel + "\"");
        if (keywordLabel != null) System.out.println("Keyword label: \"" + keywordLabel + "\"");
        if (refHeading != null) System.out.println("Reference heading: \"" + refHeading + "\"");
        System.out.println("Reference entry pattern: " + entryPattern);
        System.out.println();

        // === Generate Template JSON ===
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode template = mapper.createObjectNode();

        template.put("journal_id", journalId);
        template.put("name", "Auto-generated from " + new File(inputPdfName).getName());
        template.put("auto_generated", true);

        // Title rules
        ObjectNode titleRules = template.putObject("title_rules");
        ArrayNode skipArr = titleRules.putArray("skip_labels");
        for (String s : skipLabels) skipArr.add(s);
        titleRules.put("strategy", "largest_font_excluding_skips");
        titleRules.put("page", 0);
        titleRules.put("position", "upper_half");

        // Author rules
        ObjectNode authorRules = template.putObject("author_rules");
        ArrayNode exclArr = authorRules.putArray("exclude_patterns");
        for (String p : excludePatterns) exclArr.add(p);
        authorRules.putArray("stop_before_patterns");
        authorRules.put("separator", ",");
        authorRules.put("affiliation_marker", "superscript");

        // Abstract rules
        ObjectNode abstractRules = template.putObject("abstract_rules");
        ArrayNode absLabels = abstractRules.putArray("labels");
        if (abstractLabel != null) absLabels.add(abstractLabel);
        absLabels.add("Abstract").add("\uCD08\uB85D").add("\uC694\uC57D").add("Purpose").add("\uAD6D\uBB38\uCD08\uB85D");
        ArrayNode endLabels = abstractRules.putArray("end_labels");
        if (keywordLabel != null) endLabels.add(keywordLabel);
        endLabels.add("Keywords").add("Key words").add("\uD0A4\uC6CC\uB4DC").add("\uD575\uC2EC\uC5B4").add("\uC8FC\uC694\uC5B4");

        // Keyword rules
        ObjectNode kwRules = template.putObject("keyword_rules");
        ArrayNode kwLabels = kwRules.putArray("labels");
        if (keywordLabel != null) kwLabels.add(keywordLabel);
        kwLabels.add("Keywords").add("Key words").add("\uD0A4\uC6CC\uB4DC").add("\uD575\uC2EC\uC5B4").add("\uC8FC\uC694\uC5B4");
        kwRules.putArray("separators").add(",").add(";").add("\u00B7");

        // Reference rules
        ObjectNode refRules = template.putObject("reference_rules");
        ArrayNode refPatterns = refRules.putArray("heading_patterns");
        if (refHeading != null) refPatterns.add(refHeading);
        refPatterns.add("References").add("REFERENCES").add("\uCC38\uACE0\uBB38\uD5CC");
        refRules.put("entry_pattern", entryPattern);

        // Also generate registry entry
        ObjectNode registryEntry = mapper.createObjectNode();
        registryEntry.put("journal_id", journalId);
        ArrayNode doiPrefixes = registryEntry.putArray("doi_prefixes");
        if (doiPrefix != null) doiPrefixes.add(doiPrefix);
        ArrayNode issnArr = registryEntry.putArray("issn");
        if (issn != null) issnArr.add(issn);
        registryEntry.putArray("name_patterns");

        // Save files
        if (outputDir == null) outputDir = ".";
        Path templateDir = Paths.get(outputDir);
        Files.createDirectories(templateDir);

        Path templateFile = templateDir.resolve(journalId + ".json");
        mapper.writeValue(templateFile.toFile(), template);
        System.out.println("Template saved: " + templateFile);

        Path registryFile = templateDir.resolve(journalId + ".registry-entry.json");
        mapper.writeValue(registryFile.toFile(), registryEntry);
        System.out.println("Registry entry saved: " + registryFile);
        System.out.println();
        System.out.println("To use: copy " + journalId + ".json to paper-templates/ directory");
        System.out.println("        and add the registry entry to _registry.json");
    }

    static class ElementInfo {
        final String text;
        final double fontSize;
        final boolean isHeading;
        final boolean isBold;
        final int page;

        ElementInfo(String text, double fontSize, boolean isHeading, boolean isBold, int page) {
            this.text = text;
            this.fontSize = fontSize;
            this.isHeading = isHeading;
            this.isBold = isBold;
            this.page = page;
        }
    }

    private static ElementInfo extractInfo(IObject obj, int pageNum) {
        if (obj instanceof SemanticHeading) {
            String text = getTextValue(obj);
            if (text.trim().isEmpty()) return null;
            return new ElementInfo(text.trim(), getMaxFontSize(obj), true, true, pageNum);
        } else if (obj instanceof SemanticParagraph) {
            String text = getTextValue(obj);
            if (text.trim().isEmpty()) return null;
            return new ElementInfo(text.trim(), getMaxFontSize(obj), false, isBold(obj), pageNum);
        } else if (obj instanceof SemanticTextNode) {
            SemanticTextNode tn = (SemanticTextNode) obj;
            String text = tn.getValue();
            if (text == null || text.trim().isEmpty()) return null;
            return new ElementInfo(text.trim(), tn.getFontSize(), false, tn.getFontWeight() >= 700, pageNum);
        }
        return null;
    }

    private static String getTextValue(IObject obj) {
        if (obj instanceof SemanticTextNode) return ((SemanticTextNode) obj).getValue();
        StringBuilder sb = new StringBuilder();
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                String t = getTextValue(child);
                if (t != null && !t.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    private static double getMaxFontSize(IObject obj) {
        if (obj instanceof SemanticTextNode) return ((SemanticTextNode) obj).getFontSize();
        double max = 0;
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                max = Math.max(max, getMaxFontSize(child));
            }
        }
        return max;
    }

    private static boolean isBold(IObject obj) {
        if (obj instanceof SemanticTextNode) return ((SemanticTextNode) obj).getFontWeight() >= 700;
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                if (isBold(child)) return true;
            }
        }
        return false;
    }
}
