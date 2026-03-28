package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.paper.*;
import java.util.*;

public class TemplateAutoGeneratorTest {

    @Test
    void testNoGenerationWithFewerThan10Results() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) results.add(createHighConfDoc());
        assertTrue(TemplateAutoGenerator.tryGenerate("test", results).isEmpty());
    }

    @Test
    void testGeneratesTemplateWith10ConsistentResults() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) results.add(createHighConfDoc());
        Optional<JsonNode> template = TemplateAutoGenerator.tryGenerate("test-journal", results);
        assertTrue(template.isPresent());
        assertEquals("test-journal", template.get().get("journal_id").asText());
        assertTrue(template.get().has("title_rules"));
        assertTrue(template.get().has("author_rules"));
    }

    @Test
    void testNoGenerationWithLowConfidence() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PaperDocument doc = new PaperDocument("test.pdf", 10);
            doc.setTitle("Title");
            doc.setConfidence("title", 0.5);
            results.add(doc);
        }
        assertTrue(TemplateAutoGenerator.tryGenerate("test", results).isEmpty());
    }

    private PaperDocument createHighConfDoc() {
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        doc.setTitle("Title");
        doc.setConfidence("title", 0.95);
        doc.setConfidence("authors", 0.92);
        doc.setConfidence("abstract", 0.90);
        return doc;
    }
}
