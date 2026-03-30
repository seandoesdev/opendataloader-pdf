package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JournalFingerprinter {
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");

    public static String identify(String firstPageText, TemplateRegistry registry) {
        // 1. Try DOI prefix matching
        String doi = extractDoi(firstPageText);
        if (doi != null) {
            JsonNode template = registry.findTemplate(doi);
            if (template != null && template.has("journal_id")) {
                return template.get("journal_id").asText();
            }
        }
        // 2. Try ISSN / name pattern matching
        JsonNode template = registry.findTemplateByText(firstPageText);
        if (template != null && template.has("journal_id")) {
            return template.get("journal_id").asText();
        }
        return "default";
    }

    public static String extractDoi(String text) {
        if (text == null) return null;
        Matcher m = DOI_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }
}
