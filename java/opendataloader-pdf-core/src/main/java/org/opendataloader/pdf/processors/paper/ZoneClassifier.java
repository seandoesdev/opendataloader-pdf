package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.opendataloader.pdf.paper.ZoneType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZoneClassifier {

    private static final Logger logger = Logger.getLogger(ZoneClassifier.class.getName());

    private final Map<ZoneType, ZoneTypeWeights> weights;

    public static class ZoneTypeWeights {
        final double threshold;
        final Map<String, Double> featureWeights;

        public ZoneTypeWeights(double threshold, Map<String, Double> featureWeights) {
            this.threshold = threshold;
            this.featureWeights = featureWeights;
        }
    }

    private ZoneClassifier(Map<ZoneType, ZoneTypeWeights> weights) {
        this.weights = weights;
    }

    public static ZoneClassifier withDefaultWeights() {
        try (InputStream is = ZoneClassifier.class.getResourceAsStream("/paper-zone-weights.json")) {
            if (is != null) {
                return fromStream(is);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load paper-zone-weights.json from classpath, using hardcoded defaults", e);
        }
        return new ZoneClassifier(buildHardcodedWeights());
    }

    public static ZoneClassifier fromFile(String path) {
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            return fromStream(is);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load weights from file: " + path + ", using hardcoded defaults", e);
            return new ZoneClassifier(buildHardcodedWeights());
        }
    }

    private static ZoneClassifier fromStream(InputStream is) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(is);
        JsonNode zoneTypes = root.get("zone_types");

        Map<ZoneType, ZoneTypeWeights> weights = new EnumMap<>(ZoneType.class);
        Iterator<Map.Entry<String, JsonNode>> fields = zoneTypes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                ZoneType type = ZoneType.valueOf(entry.getKey());
                JsonNode typeNode = entry.getValue();
                double threshold = typeNode.get("threshold").asDouble();
                Map<String, Double> featureWeights = new LinkedHashMap<>();
                JsonNode featuresNode = typeNode.get("features");
                Iterator<Map.Entry<String, JsonNode>> featureFields = featuresNode.fields();
                while (featureFields.hasNext()) {
                    Map.Entry<String, JsonNode> fe = featureFields.next();
                    featureWeights.put(fe.getKey(), fe.getValue().get("weight").asDouble());
                }
                weights.put(type, new ZoneTypeWeights(threshold, featureWeights));
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Unknown zone type in weights file: " + entry.getKey());
            }
        }
        return new ZoneClassifier(weights);
    }

    public void classify(List<Zone> zones) {
        // Pass 1: score each zone independently (no context features)
        for (Zone zone : zones) {
            scoreZone(zone, null, null);
        }

        // Pass 2: re-score with context and keep higher score
        for (int i = 0; i < zones.size(); i++) {
            Zone prev = i > 0 ? zones.get(i - 1) : null;
            Zone next = i < zones.size() - 1 ? zones.get(i + 1) : null;
            Zone zone = zones.get(i);

            ZoneType pass1Type = zone.getType();
            double pass1Confidence = zone.getConfidence();

            scoreZone(zone, prev, next);

            // Keep pass 2 result only if it scores higher
            if (pass1Confidence > zone.getConfidence()) {
                zone.setType(pass1Type);
                zone.setConfidence(pass1Confidence);
            }
        }
    }

    private void scoreZone(Zone zone, Zone prevZone, Zone nextZone) {
        ZoneFeatures f = zone.getFeatures();
        String text = zone.getTextContent();

        ZoneType bestType = ZoneType.UNKNOWN;
        double bestScore = 0.0;

        for (Map.Entry<ZoneType, ZoneTypeWeights> entry : weights.entrySet()) {
            ZoneType type = entry.getKey();
            ZoneTypeWeights tw = entry.getValue();
            double score = 0.0;

            for (Map.Entry<String, Double> fe : tw.featureWeights.entrySet()) {
                String featureName = fe.getKey();
                double weight = fe.getValue();

                if (evaluateFeature(featureName, type, f, text, prevZone, nextZone)) {
                    score += weight;
                }
            }

            if (score >= tw.threshold && score > bestScore) {
                bestScore = score;
                bestType = type;
            }
        }

        zone.setType(bestType);
        zone.setConfidence(bestScore);
    }

    private boolean evaluateFeature(String featureName, ZoneType targetType, ZoneFeatures f,
                                     String text, Zone prevZone, Zone nextZone) {
        switch (featureName) {
            case "pageIndex_first":
                return f.getPageIndex() == 0;
            case "pageIndex_early":
                return f.getPageIndex() <= 1;
            case "pageIndex_late":
                return f.getPageRatio() > 0.6;
            case "position_upper":
                return f.getRelativeY() < 0.33;
            case "position_extreme":
                return f.getRelativeY() < 0.1 || f.getRelativeY() > 0.9;
            case "fontSizeRatio_large":
                return f.getFontSizeRatio() > 1.5;
            case "fontSizeRatio_medium":
                return f.getFontSizeRatio() >= 1.1 && f.getFontSizeRatio() <= 1.5;
            case "fontSizeRatio_normal":
                if (targetType == ZoneType.BODY_TEXT) {
                    return f.getFontSizeRatio() >= 0.9 && f.getFontSizeRatio() <= 1.1;
                }
                return f.getFontSizeRatio() >= 0.7 && f.getFontSizeRatio() <= 1.2;
            case "fontSizeRatio_small":
                if (targetType == ZoneType.PAGE_METADATA) {
                    return f.getFontSizeRatio() < 0.8;
                }
                return f.getFontSizeRatio() < 1.0;
            case "isBold":
                return f.isBold();
            case "not_bold":
                return !f.isBold();
            case "isCentered":
                return f.isCentered();
            case "isItalic_or_indented":
                return f.isItalic();
            case "isHeading":
                return f.isHeading();
            case "lineCount_short":
                if (targetType == ZoneType.TITLE) return f.getLineCount() <= 4;
                if (targetType == ZoneType.BODY_HEADING) return f.getLineCount() <= 2;
                if (targetType == ZoneType.KEYWORDS) return f.getLineCount() <= 3;
                if (targetType == ZoneType.PAGE_METADATA) return f.getLineCount() <= 3;
                if (targetType == ZoneType.ACKNOWLEDGMENT) return f.getLineCount() <= 10;
                return f.getLineCount() <= 4;
            case "lineCount_medium":
                return f.getLineCount() >= 3 && f.getLineCount() <= 20;
            case "lineCount_multiple":
                return f.getLineCount() >= 2;
            case "textDensity_high":
                if (targetType == ZoneType.ABSTRACT) return f.getTextDensity() > 0.7;
                return f.getTextDensity() > 0.5;
            case "textDensity_medium":
                return f.getTextDensity() >= 0.3 && f.getTextDensity() <= 0.7;
            case "hasEmail":
                return f.isHasEmailPattern();
            case "hasSuperscript":
                return f.isHasSuperscript();
            case "hasDoiPattern":
                return f.isHasDoiPattern();
            case "hasNumberPrefix":
                return f.isHasNumberPrefix();
            case "not_number_prefix":
                return !f.isHasNumberPrefix();
            case "avgLineLength_short":
                return f.getAvgLineLength() < 50;
            case "not_first_page_top":
                return !(f.getPageIndex() == 0 && f.getRelativeY() < 0.2);

            // Context features (pass 2 only)
            case "prevZone_title":
                return prevZone != null && prevZone.getType() == ZoneType.TITLE;
            case "prevZone_context":
                return prevZone != null &&
                        (prevZone.getType() == ZoneType.AUTHOR_BLOCK ||
                         prevZone.getType() == ZoneType.KEYWORDS ||
                         prevZone.getType() == ZoneType.ABSTRACT);
            case "prevZone_reference":
                return prevZone != null &&
                        (prevZone.getType() == ZoneType.REFERENCE_HEADING ||
                         prevZone.getType() == ZoneType.REFERENCE_BODY);
            case "prevZone_body":
                return prevZone != null &&
                        (prevZone.getType() == ZoneType.BODY_TEXT ||
                         prevZone.getType() == ZoneType.BODY_HEADING);

            // Keyword features
            case "keyword_bonus":
                return text != null && containsAny(text, "Abstract", "ABSTRACT", "초록", "요약", "요 약");
            case "keyword_match":
                if (targetType == ZoneType.REFERENCE_HEADING) {
                    return text != null && containsAny(text, "References", "REFERENCES", "참고문헌", "참고 문헌", "Bibliography");
                }
                if (targetType == ZoneType.ACKNOWLEDGMENT) {
                    return text != null && containsAny(text, "Acknowledgment", "Acknowledgement", "감사의 글", "사사");
                }
                return false;
            case "keyword_label":
                return text != null && containsAny(text, "Keywords", "Key words", "키워드", "핵심어", "주제어");

            default:
                logger.log(Level.FINE, "Unknown feature: " + featureName);
                return false;
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static Map<ZoneType, ZoneTypeWeights> buildHardcodedWeights() {
        Map<ZoneType, ZoneTypeWeights> weights = new EnumMap<>(ZoneType.class);

        weights.put(ZoneType.TITLE, new ZoneTypeWeights(0.60, mapOf(
                "pageIndex_first", 0.30, "position_upper", 0.20, "fontSizeRatio_large", 0.25,
                "isBold", 0.10, "isCentered", 0.10, "lineCount_short", 0.05)));

        weights.put(ZoneType.AUTHOR_BLOCK, new ZoneTypeWeights(0.55, mapOf(
                "pageIndex_first", 0.20, "prevZone_title", 0.30, "hasEmail", 0.15,
                "hasSuperscript", 0.15, "fontSizeRatio_normal", 0.10, "isCentered", 0.10)));

        weights.put(ZoneType.ABSTRACT, new ZoneTypeWeights(0.55, mapOf(
                "pageIndex_early", 0.15, "prevZone_context", 0.25, "textDensity_high", 0.15,
                "isItalic_or_indented", 0.10, "keyword_bonus", 0.30, "lineCount_medium", 0.05)));

        weights.put(ZoneType.KEYWORDS, new ZoneTypeWeights(0.55, mapOf(
                "pageIndex_early", 0.15, "prevZone_context", 0.20, "keyword_label", 0.35,
                "lineCount_short", 0.15, "fontSizeRatio_small", 0.10)));

        weights.put(ZoneType.BODY_HEADING, new ZoneTypeWeights(0.55, mapOf(
                "isHeading", 0.40, "fontSizeRatio_medium", 0.20, "isBold", 0.15,
                "lineCount_short", 0.15, "not_first_page_top", 0.10)));

        weights.put(ZoneType.BODY_TEXT, new ZoneTypeWeights(0.40, mapOf(
                "fontSizeRatio_normal", 0.30, "textDensity_high", 0.25, "lineCount_multiple", 0.15,
                "not_bold", 0.10, "not_number_prefix", 0.10)));

        weights.put(ZoneType.REFERENCE_HEADING, new ZoneTypeWeights(0.60, mapOf(
                "isHeading", 0.25, "keyword_match", 0.45, "pageIndex_late", 0.15,
                "isBold", 0.10)));

        weights.put(ZoneType.REFERENCE_BODY, new ZoneTypeWeights(0.55, mapOf(
                "pageIndex_late", 0.20, "prevZone_reference", 0.30, "hasNumberPrefix", 0.25,
                "avgLineLength_short", 0.10, "textDensity_medium", 0.10)));

        weights.put(ZoneType.PAGE_METADATA, new ZoneTypeWeights(0.55, mapOf(
                "position_extreme", 0.30, "fontSizeRatio_small", 0.20, "hasDoiPattern", 0.25,
                "lineCount_short", 0.15, "pageIndex_first", 0.10)));

        weights.put(ZoneType.ACKNOWLEDGMENT, new ZoneTypeWeights(0.60, mapOf(
                "keyword_match", 0.45, "pageIndex_late", 0.20, "prevZone_body", 0.15,
                "lineCount_short", 0.10)));

        return weights;
    }

    private static Map<String, Double> mapOf(String k1, double v1, String k2, double v2,
                                              String k3, double v3, String k4, double v4,
                                              String k5, double v5, String k6, double v6) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(k1, v1); map.put(k2, v2); map.put(k3, v3);
        map.put(k4, v4); map.put(k5, v5); map.put(k6, v6);
        return map;
    }

    private static Map<String, Double> mapOf(String k1, double v1, String k2, double v2,
                                              String k3, double v3, String k4, double v4,
                                              String k5, double v5) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(k1, v1); map.put(k2, v2); map.put(k3, v3);
        map.put(k4, v4); map.put(k5, v5);
        return map;
    }

    private static Map<String, Double> mapOf(String k1, double v1, String k2, double v2,
                                              String k3, double v3, String k4, double v4) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(k1, v1); map.put(k2, v2); map.put(k3, v3); map.put(k4, v4);
        return map;
    }
}
