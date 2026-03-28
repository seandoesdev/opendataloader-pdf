package org.opendataloader.pdf.processors.paper.crf;

import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts 40+ numeric features from a Zone for CRF-based zone classification.
 */
public class CRFFeatureExtractor {

    private static final double PAGE_WIDTH = 612.0;
    private static final double PAGE_HEIGHT = 792.0;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,}/\\S+");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern SECTION_NUMBER_PATTERN = Pattern.compile(
            "^\\s*([\\u2160-\\u216F]\\.|\\d+\\.|[IVX]+\\.)");
    private static final Pattern ABSTRACT_LABEL = Pattern.compile(
            "(?i)(\\bAbstract\\b|\uCD08\uB85D|\uC694\uC57D|\uAD6D\uBB38\uCD08\uB85D)");
    private static final Pattern KEYWORD_LABEL = Pattern.compile(
            "(?i)(\\bKeywords?\\b|\uD0A4\uC6CC\uB4DC|\uD575\uC2EC\uC5B4|\uC8FC\uC694\uC5B4)");
    private static final Pattern REF_LABEL = Pattern.compile(
            "(?i)(\\bReferences\\b|\uCC38\uACE0\uBB38\uD5CC)");
    private static final Pattern ACK_LABEL = Pattern.compile(
            "(?i)(\\bAcknowledgm(ent|ents)\\b|\uAC10\uC0AC\uC758 \uAE00)");

    public Map<String, Double> extractNumericFeatures(Zone zone) {
        Map<String, Double> features = new LinkedHashMap<>();
        ZoneFeatures zf = zone.getFeatures();
        BoundingBox bounds = zone.getBounds();
        String text = zone.getTextContent();

        // Position features (8)
        features.put("relativeY", zf.getRelativeY());
        features.put("pageIndex", (double) zf.getPageIndex());
        features.put("pageRatio", zf.getPageRatio());
        features.put("isFirstPage", zf.getPageIndex() == 0 ? 1.0 : 0.0);
        features.put("isLastQuarter", zf.getPageRatio() >= 0.75 ? 1.0 : 0.0);
        double width = bounds.getRightX() - bounds.getLeftX();
        double height = bounds.getTopY() - bounds.getBottomY();
        features.put("widthRatio", width / PAGE_WIDTH);
        features.put("heightRatio", height / PAGE_HEIGHT);
        features.put("zoneArea", (width * height) / (PAGE_WIDTH * PAGE_HEIGHT));

        // Font features (6)
        features.put("avgFontSize", zf.getAvgFontSize());
        features.put("maxFontSize", zf.getMaxFontSize());
        features.put("fontSizeRatio", zf.getFontSizeRatio());
        features.put("isBold", zf.isBold() ? 1.0 : 0.0);
        features.put("isCentered", zf.isCentered() ? 1.0 : 0.0);
        features.put("isItalic", zf.isItalic() ? 1.0 : 0.0);

        // Text stats (8)
        features.put("lineCount", (double) zf.getLineCount());
        features.put("avgLineLength", zf.getAvgLineLength());
        features.put("textDensity", zf.getTextDensity());
        int charCount = text.length();
        features.put("charCount", (double) charCount);
        features.put("koreanRatio", computeKoreanRatio(text, charCount));
        features.put("digitRatio", computeCharRatio(text, charCount, Character::isDigit));
        features.put("punctuationRatio", computePunctuationRatio(text, charCount));
        features.put("wordCount", (double) countWords(text));

        // Pattern features (10)
        features.put("hasEmail", boolFeature(EMAIL_PATTERN.matcher(text).find()));
        features.put("hasDoi", boolFeature(DOI_PATTERN.matcher(text).find()));
        features.put("hasUrl", boolFeature(URL_PATTERN.matcher(text).find()));
        features.put("hasNumberPrefix", boolFeature(zf.isHasNumberPrefix()));
        features.put("hasSuperscript", boolFeature(zf.isHasSuperscript()));
        features.put("hasQuotedText", boolFeature(text.contains("\"") || text.contains("\u201C")));
        features.put("startsWithCapital",
                boolFeature(!text.isEmpty() && Character.isUpperCase(text.charAt(0))));
        features.put("containsYear", boolFeature(YEAR_PATTERN.matcher(text).find()));
        features.put("hasCommaList", boolFeature(countOccurrences(text, ',') >= 3));
        features.put("hasParentheses", boolFeature(text.contains("(") && text.contains(")")));

        // Keyword features (5)
        features.put("containsAbstractLabel", boolFeature(ABSTRACT_LABEL.matcher(text).find()));
        features.put("containsKeywordLabel", boolFeature(KEYWORD_LABEL.matcher(text).find()));
        features.put("containsRefLabel", boolFeature(REF_LABEL.matcher(text).find()));
        features.put("containsSectionNumber",
                boolFeature(SECTION_NUMBER_PATTERN.matcher(text).find()));
        features.put("containsAckLabel", boolFeature(ACK_LABEL.matcher(text).find()));

        // Semantic features (1)
        features.put("isHeading", boolFeature(zf.isHeading()));

        return features;
    }

    public String toMalletFeatureString(Zone zone) {
        Map<String, Double> features = extractNumericFeatures(zone);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static double boolFeature(boolean value) {
        return value ? 1.0 : 0.0;
    }

    private static double computeKoreanRatio(String text, int totalChars) {
        if (totalChars == 0) {
            return 0.0;
        }
        int koreanCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isKorean(c)) {
                koreanCount++;
            }
        }
        return (double) koreanCount / totalChars;
    }

    private static boolean isKorean(char c) {
        return (c >= '\uAC00' && c <= '\uD7A3')
                || (c >= '\u3131' && c <= '\u318E')
                || (c >= '\u1100' && c <= '\u11FF');
    }

    private static double computeCharRatio(String text, int totalChars,
                                           java.util.function.IntPredicate predicate) {
        if (totalChars == 0) {
            return 0.0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (predicate.test(text.charAt(i))) {
                count++;
            }
        }
        return (double) count / totalChars;
    }

    private static double computePunctuationRatio(String text, int totalChars) {
        if (totalChars == 0) {
            return 0.0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                count++;
            }
        }
        return (double) count / totalChars;
    }

    private static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static int countOccurrences(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }
}
