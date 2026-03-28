package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.opendataloader.pdf.paper.ZoneType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneClassifierTest {

    private final ZoneClassifier classifier = ZoneClassifier.withDefaultWeights();

    private Zone createZone(ZoneFeatures features) {
        return new Zone(0, null, Collections.emptyList(), features);
    }

    @Test
    void testTitleZoneClassification() {
        // First page, upper position, large font, bold, centered, short line count
        ZoneFeatures features = new ZoneFeatures(
                0.15,   // relativeY - upper
                18.0,   // avgFontSize
                20.0,   // maxFontSize
                2.0,    // fontSizeRatio - large (> 1.5)
                true,   // bold
                true,   // centered
                false,  // italic
                0.5,    // textDensity
                2,      // lineCount - short
                60.0,   // avgLineLength
                false,  // hasNumberPrefix
                false,  // hasEmailPattern
                false,  // hasDoiPattern
                false,  // hasSuperscript
                0,      // pageIndex - first page
                0.0,    // pageRatio
                false   // isHeading
        );

        Zone zone = createZone(features);
        zone.setTextOverride("A Study of Machine Learning Approaches");
        classifier.classify(Collections.singletonList(zone));

        assertEquals(ZoneType.TITLE, zone.getType());
        assertTrue(zone.getConfidence() >= 0.6, "Confidence should be >= 0.6, was: " + zone.getConfidence());
    }

    @Test
    void testReferenceBodyAfterReferenceHeading() {
        // Reference heading zone
        ZoneFeatures headingFeatures = new ZoneFeatures(
                0.1,    // relativeY
                14.0,   // avgFontSize
                14.0,   // maxFontSize
                1.6,    // fontSizeRatio - large (outside medium range)
                true,   // bold
                false,  // centered
                false,  // italic
                0.3,    // textDensity
                1,      // lineCount
                30.0,   // avgLineLength
                false,  // hasNumberPrefix
                false,  // hasEmailPattern
                false,  // hasDoiPattern
                false,  // hasSuperscript
                8,      // pageIndex - late page
                0.8,    // pageRatio - late
                true    // isHeading
        );

        Zone headingZone = createZone(headingFeatures);
        headingZone.setTextOverride("References");

        // Reference body zone
        ZoneFeatures bodyFeatures = new ZoneFeatures(
                0.25,   // relativeY
                10.0,   // avgFontSize
                10.0,   // maxFontSize
                0.9,    // fontSizeRatio
                false,  // bold
                false,  // centered
                false,  // italic
                0.5,    // textDensity
                15,     // lineCount
                40.0,   // avgLineLength
                true,   // hasNumberPrefix
                false,  // hasEmailPattern
                false,  // hasDoiPattern
                false,  // hasSuperscript
                8,      // pageIndex - late page
                0.8,    // pageRatio - late
                false   // isHeading
        );

        Zone bodyZone = createZone(bodyFeatures);
        bodyZone.setTextOverride("[1] Smith, J. (2020). A paper about things. Journal of Things, 1(1), 1-10.");

        List<Zone> zones = Arrays.asList(headingZone, bodyZone);
        classifier.classify(zones);

        assertEquals(ZoneType.REFERENCE_HEADING, headingZone.getType());
        assertEquals(ZoneType.REFERENCE_BODY, bodyZone.getType());
    }

    @Test
    void testContextPassImprovesClassification() {
        // Title zone
        ZoneFeatures titleFeatures = new ZoneFeatures(
                0.1,    // relativeY
                20.0,   // avgFontSize
                22.0,   // maxFontSize
                2.0,    // fontSizeRatio - large
                true,   // bold
                true,   // centered
                false,  // italic
                0.4,    // textDensity
                2,      // lineCount
                50.0,   // avgLineLength
                false,  // hasNumberPrefix
                false,  // hasEmailPattern
                false,  // hasDoiPattern
                false,  // hasSuperscript
                0,      // pageIndex
                0.0,    // pageRatio
                false   // isHeading
        );

        Zone titleZone = createZone(titleFeatures);
        titleZone.setTextOverride("Deep Learning for Natural Language Processing");

        // Author zone - first page, has email, centered, has superscript
        ZoneFeatures authorFeatures = new ZoneFeatures(
                0.25,   // relativeY
                11.0,   // avgFontSize
                12.0,   // maxFontSize
                1.0,    // fontSizeRatio - normal (0.7-1.2)
                false,  // bold
                true,   // centered
                false,  // italic
                0.3,    // textDensity
                3,      // lineCount
                40.0,   // avgLineLength
                false,  // hasNumberPrefix
                true,   // hasEmailPattern
                false,  // hasDoiPattern
                true,   // hasSuperscript
                0,      // pageIndex
                0.0,    // pageRatio
                false   // isHeading
        );

        Zone authorZone = createZone(authorFeatures);
        authorZone.setTextOverride("John Smith¹, Jane Doe²\njsmith@university.edu");

        List<Zone> zones = Arrays.asList(titleZone, authorZone);
        classifier.classify(zones);

        assertEquals(ZoneType.TITLE, titleZone.getType());
        assertEquals(ZoneType.AUTHOR_BLOCK, authorZone.getType());
    }

    @Test
    void testUnknownOrBodyTextWhenBelowThreshold() {
        // Bland features that don't strongly match any type
        ZoneFeatures features = new ZoneFeatures(
                0.5,    // relativeY - middle
                12.0,   // avgFontSize
                12.0,   // maxFontSize
                1.0,    // fontSizeRatio - normal
                false,  // bold
                false,  // centered
                false,  // italic
                0.2,    // textDensity - low
                1,      // lineCount - single line
                25.0,   // avgLineLength
                false,  // hasNumberPrefix
                false,  // hasEmailPattern
                false,  // hasDoiPattern
                false,  // hasSuperscript
                3,      // pageIndex - middle page
                0.3,    // pageRatio
                false   // isHeading
        );

        Zone zone = createZone(features);
        zone.setTextOverride("Some generic text");
        classifier.classify(Collections.singletonList(zone));

        assertTrue(zone.getType() == ZoneType.BODY_TEXT || zone.getType() == ZoneType.UNKNOWN,
                "Expected BODY_TEXT or UNKNOWN, got: " + zone.getType());
    }
}
