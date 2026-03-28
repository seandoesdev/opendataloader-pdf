package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResultMergerTest {

    @Test
    void testPicksHigherConfidenceTitle() {
        PaperDocument templateResult = new PaperDocument("test.pdf", 10);
        templateResult.setTitle("Template Title");
        templateResult.setConfidence("title", 0.95);

        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setTitle("Rule Title");
        ruleResult.setConfidence("title", 0.65);

        PaperDocument merged = ResultMerger.merge(templateResult, null, ruleResult);
        assertEquals("Template Title", merged.getTitle());
    }

    @Test
    void testFallsBackToRuleWhenNoTemplate() {
        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setTitle("Rule Title");
        ruleResult.setConfidence("title", 0.65);

        PaperDocument merged = ResultMerger.merge(null, null, ruleResult);
        assertEquals("Rule Title", merged.getTitle());
    }

    @Test
    void testMergesFieldsFromDifferentLayers() {
        PaperDocument templateResult = new PaperDocument("test.pdf", 10);
        templateResult.setTitle("Template Title");
        templateResult.setConfidence("title", 0.95);

        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setDoi("10.1234/test");
        ruleResult.setConfidence("doi", 0.80);

        PaperDocument merged = ResultMerger.merge(templateResult, null, ruleResult);
        assertEquals("Template Title", merged.getTitle());
        assertEquals("10.1234/test", merged.getDoi());
    }
}
