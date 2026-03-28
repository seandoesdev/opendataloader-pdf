package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateBasedExtractorTest {

    @Test
    void testSkipsCategoryLabel() {
        JsonNode template = loadTemplate("default.json");
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.TITLE, 1, "Original Article", 12.0, true));
        zones.add(createZone(ZoneType.TITLE, 1, "Effects of Exercise on Cardiovascular Health in Elderly Patients", 20.0, true));

        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals("Effects of Exercise on Cardiovascular Health in Elderly Patients", doc.getTitle());
        assertEquals(0.92, doc.getConfidence().get("title"), 0.001);
    }

    @Test
    void testExcludesDateFromAuthors() {
        JsonNode template = loadTemplate("default.json");
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.AUTHOR_BLOCK, 1,
            "Received: 1 August, 2025. Deok Gi Chae, Eun Kyoung Lee", 10.0, false));

        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        // "Received: 1 August" and "2025." should be excluded, only valid author names remain
        List<String> authorNames = new ArrayList<>();
        for (PaperAuthor a : doc.getAuthors()) {
            authorNames.add(a.getName());
        }
        assertTrue(authorNames.contains("Deok Gi Chae"), "Should contain Deok Gi Chae");
        assertTrue(authorNames.contains("Eun Kyoung Lee"), "Should contain Eun Kyoung Lee");
        // Ensure date fragments are not included
        for (String name : authorNames) {
            assertFalse(name.contains("Received"), "Should not contain date text: " + name);
            assertFalse(name.contains("August"), "Should not contain month: " + name);
        }
        assertEquals(0.90, doc.getConfidence().get("authors"), 0.001);
    }

    @Test
    void testAbstractExtraction() {
        JsonNode template = loadTemplate("default.json");
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.ABSTRACT, 1,
            "Purpose This study investigates the effects of regular physical exercise on cardiovascular health outcomes in elderly patients over a two-year period.",
            10.0, false));

        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertNotNull(doc.getAbstractText());
        assertFalse(doc.getAbstractText().startsWith("Purpose"), "Label should be stripped");
        assertTrue(doc.getAbstractText().contains("This study investigates"));
        assertEquals(0.92, doc.getConfidence().get("abstract"), 0.001);
    }

    @Test
    void testReferenceEntryPattern() {
        JsonNode template = loadTemplate("default.json");
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.REFERENCE_BODY, 5,
            "[1] Smith, J. \"A study of things.\" Journal of Stuff, 2020. [2] Lee, K. \"Another study.\" Korean J., 2021.",
            9.0, false));

        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals(2, doc.getReferences().size());
        assertEquals(0.88, doc.getConfidence().get("references"), 0.001);
    }

    @Test
    void testDefaultTemplateAlsoWorks() {
        // The default.json template should also skip "Original Article"
        JsonNode template = loadTemplate("default.json");
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.TITLE, 1, "Original Article", 12.0, true));
        zones.add(createZone(ZoneType.TITLE, 1, "A Comprehensive Review of Machine Learning in Healthcare Systems", 18.0, true));

        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals("A Comprehensive Review of Machine Learning in Healthcare Systems", doc.getTitle());
    }

    private Zone createZone(ZoneType type, int page, String text, double fontSize, boolean bold) {
        ZoneFeatures features = new ZoneFeatures(0.1, fontSize, fontSize,
            fontSize / 10.0, bold, true, false, 0.5, 1, 50,
            false, false, false, false, page - 1, 0.0, false);
        Zone zone = new Zone(page, new BoundingBox(0, 72, 100, 540, 700),
            new ArrayList<>(), features);
        zone.setType(type);
        zone.setConfidence(0.8);
        zone.setTextOverride(text);
        return zone;
    }

    private JsonNode loadTemplate(String name) {
        try {
            return new ObjectMapper().readTree(
                getClass().getResourceAsStream("/paper-templates/" + name));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
