package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthorSectionTest {

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

    // ---- AuthorExtractor tests ----

    @Test
    void testAuthorExtractionSimple() {
        Zone zone = createTextZone(ZoneType.AUTHOR_BLOCK, 1, "John Smith, Jane Doe");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        AuthorExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals(2, doc.getAuthors().size());
        assertEquals("John Smith", doc.getAuthors().get(0).getName());
        assertEquals("Jane Doe", doc.getAuthors().get(1).getName());
    }

    @Test
    void testAuthorWithEmail() {
        Zone zone = createTextZone(ZoneType.AUTHOR_BLOCK, 1, "John Smith, john@example.com");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        AuthorExtractor.extract(Collections.singletonList(zone), doc);
        assertFalse(doc.getAuthors().isEmpty());
        assertNotNull(doc.getAuthors().get(0).getEmail());
        assertEquals("john@example.com", doc.getAuthors().get(0).getEmail());
    }

    @Test
    void testCorrespondingAuthorDetection() {
        Zone zone = createTextZone(ZoneType.AUTHOR_BLOCK, 1, "John Smith*, Jane Doe");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        AuthorExtractor.extract(Collections.singletonList(zone), doc);
        assertEquals(2, doc.getAuthors().size());
        assertTrue(doc.getAuthors().get(0).isCorresponding());
        assertFalse(doc.getAuthors().get(1).isCorresponding());
    }

    // ---- SectionClassifier tests ----

    @Test
    void testSectionClassification() {
        Zone zone = createTextZone(ZoneType.BODY_HEADING, 1, "1. Introduction");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        SectionClassifier.classify(Collections.singletonList(zone), doc);
        assertEquals(1, doc.getSections().size());
        assertEquals("introduction", doc.getSections().get(0).getType());
    }

    @Test
    void testKoreanSectionClassification() {
        Zone zone = createTextZone(ZoneType.BODY_HEADING, 1, "2. 연구 방법");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        SectionClassifier.classify(Collections.singletonList(zone), doc);
        assertEquals(1, doc.getSections().size());
        assertEquals("methods", doc.getSections().get(0).getType());
    }

    @Test
    void testSectionContentCollection() {
        List<Zone> zones = Arrays.asList(
                createTextZone(ZoneType.BODY_HEADING, 1, "1. Introduction"),
                createTextZone(ZoneType.BODY_TEXT, 1, "This paper presents a novel approach."),
                createTextZone(ZoneType.BODY_TEXT, 2, "We build on prior work.")
        );
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        SectionClassifier.classify(zones, doc);
        assertEquals(1, doc.getSections().size());
        PaperSection section = doc.getSections().get(0);
        assertEquals("introduction", section.getType());
        assertTrue(section.getContent().contains("This paper presents a novel approach."));
        assertTrue(section.getContent().contains("We build on prior work."));
        assertEquals(1, section.getPageStart());
        assertEquals(2, section.getPageEnd());
    }

    @Test
    void testUnmatchedSectionBecomesOther() {
        Zone zone = createTextZone(ZoneType.BODY_HEADING, 3, "3. Custom Topic");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        SectionClassifier.classify(Collections.singletonList(zone), doc);
        assertEquals(1, doc.getSections().size());
        assertEquals("other", doc.getSections().get(0).getType());
    }
}
