package org.opendataloader.pdf.processors.paper.crf;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.opendataloader.pdf.paper.ZoneType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CRFFeatureExtractorTest {

    private final CRFFeatureExtractor extractor = new CRFFeatureExtractor();

    private Zone createZone(ZoneType type, int page, String text, double fontSize, boolean bold) {
        ZoneFeatures features = new ZoneFeatures(0.1, fontSize, fontSize,
                fontSize / 10.0, bold, true, false, 0.5, 2, 30,
                false, false, false, false, page - 1, 0.1, type == ZoneType.TITLE);
        Zone zone = new Zone(page, new BoundingBox(0, 72, 100, 540, 700),
                new ArrayList<>(), features);
        zone.setType(type);
        zone.setConfidence(0.8);
        zone.setTextOverride(text);
        return zone;
    }

    @Test
    void testExtractFeaturesProducesCorrectCount() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1, "Sample body text for testing.", 10.0, false);
        Map<String, Double> features = extractor.extractNumericFeatures(zone);
        assertThat(features.size()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void testBooleanFeaturesAreZeroOrOne() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1, "Some text content.", 10.0, true);
        Map<String, Double> features = extractor.extractNumericFeatures(zone);
        String[] booleanKeys = {
                "isFirstPage", "isLastQuarter", "isBold", "isCentered", "isItalic",
                "hasEmail", "hasDoi", "hasUrl", "hasNumberPrefix", "hasSuperscript",
                "hasQuotedText", "startsWithCapital", "containsYear", "hasCommaList",
                "hasParentheses", "containsAbstractLabel", "containsKeywordLabel",
                "containsRefLabel", "containsSectionNumber", "containsAckLabel", "isHeading"
        };
        for (String key : booleanKeys) {
            assertThat(features.get(key))
                    .as("Feature '%s' should be 0.0 or 1.0", key)
                    .isIn(0.0, 1.0);
        }
    }

    @Test
    void testToMalletFeatureString() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1, "Test text.", 12.0, true);
        String featureString = extractor.toMalletFeatureString(zone);
        assertThat(featureString).contains("relativeY=");
        assertThat(featureString).contains("fontSizeRatio=");
        assertThat(featureString).contains("isBold=");
    }

    @Test
    void testKoreanRatioFeature() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1,
                "\uD55C\uAD6D\uC5B4 \uD14D\uC2A4\uD2B8 \uBD84\uC11D \uD14C\uC2A4\uD2B8",
                10.0, false);
        Map<String, Double> features = extractor.extractNumericFeatures(zone);
        assertThat(features.get("koreanRatio")).isGreaterThan(0.5);
    }

    @Test
    void testEmailDetectionFeature() {
        Zone zone = createZone(ZoneType.AUTHOR_BLOCK, 1,
                "John Doe john.doe@example.com", 10.0, false);
        Map<String, Double> features = extractor.extractNumericFeatures(zone);
        assertThat(features.get("hasEmail")).isEqualTo(1.0);
    }
}
