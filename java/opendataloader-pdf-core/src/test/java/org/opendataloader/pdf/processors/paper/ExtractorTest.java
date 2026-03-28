package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorTest {

    private Zone createTextZone(ZoneType type, int page, String text) {
        Zone zone = new Zone(page,
            new BoundingBox(0, 72, 100, 540, 700),
            new ArrayList<>(),
            new ZoneFeatures(0.5, 12, 12, 1.0, false, false, false, 0.5,
                1, 50, false, false, false, false, 0, 0.0, false));
        zone.setType(type);
        zone.setConfidence(0.9);
        zone.setTextOverride(text);
        return zone;
    }

    @Test
    void testTitleExtractsFromTitleZone() {
        Zone zone = createTextZone(ZoneType.TITLE, 1, "Deep Learning for NLP");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TitleExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals("Deep Learning for NLP", doc.getTitle());
        assertEquals("en", doc.getLanguage());
        assertEquals(0.9, doc.getConfidence().get("title"), 0.001);
    }

    @Test
    void testTitleSkipsNonTitleZones() {
        Zone zone = createTextZone(ZoneType.BODY_TEXT, 1, "Some body text");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TitleExtractor.extract(Collections.singletonList(zone), doc);
        assertNull(doc.getTitle());
    }

    @Test
    void testAbstractExtractsText() {
        Zone zone = createTextZone(ZoneType.ABSTRACT, 1, "Abstract \uBCF8 \uC5F0\uAD6C\uB294...");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        AbstractExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals("\uBCF8 \uC5F0\uAD6C\uB294...", doc.getAbstractText());
    }

    @Test
    void testAbstractRemovesKoreanLabel() {
        Zone zone = createTextZone(ZoneType.ABSTRACT, 1, "\uCD08\uB85D \uBCF8 \uC5F0\uAD6C\uB294...");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        AbstractExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals("\uBCF8 \uC5F0\uAD6C\uB294...", doc.getAbstractText());
    }

    @Test
    void testDoiExtraction() {
        Zone zone = createTextZone(ZoneType.PAGE_METADATA, 1, "DOI: 10.1234/abcd.2026.001");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        MetadataExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals("10.1234/abcd.2026.001", doc.getDoi());
    }

    @Test
    void testKeywordExtraction() {
        Zone zone = createTextZone(ZoneType.KEYWORDS, 1, "Keywords: machine learning, NLP, deep learning");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        MetadataExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals(3, doc.getKeywords().size());
        assertTrue(doc.getKeywords().contains("machine learning"));
        assertTrue(doc.getKeywords().contains("NLP"));
        assertTrue(doc.getKeywords().contains("deep learning"));
    }

    @Test
    void testKoreanKeywordExtraction() {
        Zone zone = createTextZone(ZoneType.KEYWORDS, 1, "\uD0A4\uC6CC\uB4DC: \uAE30\uACC4\uD559\uC2B5, \uC790\uC5F0\uC5B4\uCC98\uB9AC, \uB525\uB7EC\uB2DD");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        MetadataExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals(3, doc.getKeywords().size());
        assertTrue(doc.getKeywords().contains("\uAE30\uACC4\uD559\uC2B5"));
    }

    @Test
    void testVolumeIssueExtraction() {
        Zone zone = createTextZone(ZoneType.PAGE_METADATA, 1, "Vol. 53, No. 3, pp. 123-135");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        MetadataExtractor.extract(Collections.singletonList(zone), doc);
        PaperPublication pub = doc.getPublication();
        assertNotNull(pub);
        assertEquals("53", pub.getVolume());
        assertEquals("3", pub.getIssue());
        assertEquals("123-135", pub.getPages());
    }
}
