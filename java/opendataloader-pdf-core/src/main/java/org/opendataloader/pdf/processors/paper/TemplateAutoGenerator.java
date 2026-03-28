package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.opendataloader.pdf.paper.PaperDocument;
import java.util.*;
import java.util.logging.Logger;

public class TemplateAutoGenerator {
    private static final Logger LOGGER = Logger.getLogger(TemplateAutoGenerator.class.getName());
    private static final int MIN_DOCUMENTS = 10;
    private static final double MIN_AVG_CONFIDENCE = 0.85;

    public static Optional<JsonNode> tryGenerate(String journalId, List<PaperDocument> results) {
        if (results.size() < MIN_DOCUMENTS) return Optional.empty();

        // Check average confidence
        double avgTitleConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("title", 0.0)).average().orElse(0);
        double avgAuthorConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("authors", 0.0)).average().orElse(0);
        double avgAbstractConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("abstract", 0.0)).average().orElse(0);
        double overallAvg = (avgTitleConf + avgAuthorConf + avgAbstractConf) / 3.0;

        if (overallAvg < MIN_AVG_CONFIDENCE) return Optional.empty();

        // Generate template with default/broad rules
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode template = mapper.createObjectNode();
        template.put("journal_id", journalId);
        template.put("name", "Auto-generated template for " + journalId);
        template.put("auto_generated", true);
        template.put("document_count", results.size());

        // Title rules
        ObjectNode titleRules = template.putObject("title_rules");
        titleRules.putArray("skip_labels").add("Original Article").add("Review Article").add("Case Report");
        titleRules.put("strategy", "largest_font_excluding_skips");
        titleRules.put("page", 0);
        titleRules.put("position", "upper_half");

        // Author rules
        ObjectNode authorRules = template.putObject("author_rules");
        authorRules.putArray("exclude_patterns").add("Received:").add("Revised:").add("Accepted:").add("Published:");
        authorRules.putArray("stop_before_patterns");
        authorRules.put("separator", ",");
        authorRules.put("affiliation_marker", "superscript");

        // Abstract rules
        ObjectNode abstractRules = template.putObject("abstract_rules");
        abstractRules.putArray("labels").add("Abstract").add("ABSTRACT").add("초록").add("요약").add("Purpose").add("국문초록");
        abstractRules.putArray("end_labels").add("Keywords").add("Key words").add("키워드").add("핵심어").add("주요어");

        // Keyword rules
        ObjectNode keywordRules = template.putObject("keyword_rules");
        keywordRules.putArray("labels").add("Keywords").add("Key words").add("키워드").add("핵심어").add("주요어");
        keywordRules.putArray("separators").add(",").add(";").add("·");

        // Reference rules
        ObjectNode referenceRules = template.putObject("reference_rules");
        referenceRules.putArray("heading_patterns").add("References").add("REFERENCES").add("참고문헌");
        referenceRules.put("entry_pattern", "bracket_number");

        LOGGER.info("Auto-generated template for '" + journalId + "' from " + results.size() + " docs");
        return Optional.of(template);
    }
}
