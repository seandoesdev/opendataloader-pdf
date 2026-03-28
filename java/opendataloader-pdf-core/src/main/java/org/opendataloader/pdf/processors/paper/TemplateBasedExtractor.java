package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.paper.*;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts paper metadata using journal-specific template rules loaded from JSON.
 * Template sections: title_rules, author_rules, abstract_rules, keyword_rules, reference_rules.
 */
public class TemplateBasedExtractor {

    private static final Pattern BRACKET_NUMBER = Pattern.compile("\\[\\d+\\]");
    private static final Pattern DOT_NUMBER = Pattern.compile("(?m)(?=\\d+\\.\\s)");
    private static final Pattern ENTRY_NUMBER = Pattern.compile("^\\s*\\[?(\\d+)[.)\\]]?\\s*");
    private static final Pattern QUOTED_TITLE = Pattern.compile("[\"\u201c](.+?)[\"\u201d]");
    private static final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");

    public static void extract(List<Zone> zones, JsonNode template, PaperDocument doc) {
        if (template == null) return;

        extractTitle(zones, template.get("title_rules"), doc);
        extractAuthors(zones, template.get("author_rules"), doc);
        extractAbstract(zones, template.get("abstract_rules"), doc);
        extractKeywords(zones, template.get("keyword_rules"), doc);
        extractReferences(zones, template.get("reference_rules"), doc);
    }

    private static void extractTitle(List<Zone> zones, JsonNode rules, PaperDocument doc) {
        if (rules == null) return;

        Set<String> skipLabels = new HashSet<>();
        JsonNode skipNode = rules.get("skip_labels");
        if (skipNode != null && skipNode.isArray()) {
            for (JsonNode label : skipNode) {
                skipLabels.add(label.asText().toLowerCase());
            }
        }

        // Find TITLE zones, skip any matching skip_labels, pick the one with largest font
        Zone bestTitle = null;
        double bestFontSize = -1;

        for (Zone zone : zones) {
            if (zone.getType() != ZoneType.TITLE) continue;
            String text = zone.getTextContent().trim();
            if (text.isEmpty()) continue;

            // Skip if text matches a skip label (case-insensitive)
            if (skipLabels.contains(text.toLowerCase())) continue;

            double fontSize = zone.getFeatures().getMaxFontSize();
            if (fontSize > bestFontSize) {
                bestFontSize = fontSize;
                bestTitle = zone;
            }
        }

        // Fallback: if no TITLE zone passes, look for largest fontSizeRatio on page 0
        if (bestTitle == null) {
            double bestRatio = -1;
            for (Zone zone : zones) {
                if (zone.getFeatures().getPageIndex() != 0) continue;
                String text = zone.getTextContent().trim();
                if (text.isEmpty()) continue;
                if (skipLabels.contains(text.toLowerCase())) continue;

                double ratio = zone.getFeatures().getFontSizeRatio();
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestTitle = zone;
                }
            }
        }

        if (bestTitle != null) {
            String titleText = bestTitle.getTextContent().trim();
            if (PaperValidator.isValidTitle(titleText)) {
                doc.setTitle(titleText);
                doc.setConfidence("title", 0.92);

                boolean hasKorean = titleText.codePoints().anyMatch(cp ->
                    (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0x3131 && cp <= 0x318E));
                doc.setLanguage(hasKorean ? "ko" : "en");
            }
        }
    }

    private static void extractAuthors(List<Zone> zones, JsonNode rules, PaperDocument doc) {
        if (rules == null) return;

        List<Pattern> excludePatterns = new ArrayList<>();
        JsonNode excludeNode = rules.get("exclude_patterns");
        if (excludeNode != null && excludeNode.isArray()) {
            for (JsonNode pat : excludeNode) {
                excludePatterns.add(Pattern.compile(Pattern.quote(pat.asText()), Pattern.CASE_INSENSITIVE));
            }
        }

        String separator = ",";
        JsonNode sepNode = rules.get("separator");
        if (sepNode != null) {
            separator = sepNode.asText();
        }

        for (Zone zone : zones) {
            if (zone.getType() != ZoneType.AUTHOR_BLOCK) continue;
            String text = zone.getTextContent().trim();
            if (text.isEmpty()) continue;

            // Remove text matching exclude patterns and surrounding date-like content.
            // e.g., "Received: 1 August, 2025." is removed from the text before splitting.
            // Strategy: find each exclude pattern, then remove from the start of that
            // segment back to the previous separator, and forward to the next ". " boundary
            // (which typically ends a date clause).
            for (Pattern ep : excludePatterns) {
                Matcher m = ep.matcher(text);
                if (m.find()) {
                    int removeStart = m.start();
                    // Walk backwards to previous separator or start of string
                    for (int i = m.start() - 1; i >= 0; i--) {
                        char c = text.charAt(i);
                        if (c == ',' || c == ';') {
                            removeStart = i;
                            break;
                        }
                        removeStart = i;
                    }
                    // Walk forward from match end to find ". " boundary (end of date clause)
                    int removeEnd = text.length();
                    for (int i = m.end(); i < text.length() - 1; i++) {
                        if (text.charAt(i) == '.' && (text.charAt(i + 1) == ' ' || i + 1 == text.length() - 1)) {
                            removeEnd = i + 1;
                            // Skip trailing whitespace
                            while (removeEnd < text.length() && text.charAt(removeEnd) == ' ') {
                                removeEnd++;
                            }
                            break;
                        }
                    }
                    text = (text.substring(0, removeStart).trim() + " " + text.substring(removeEnd).trim()).trim();
                }
            }

            String[] parts = text.split(Pattern.quote(separator));
            for (String part : parts) {
                String name = part.trim();
                if (name.isEmpty()) continue;

                // Clean superscript markers
                name = name.replaceAll("[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡§]", "").trim();
                if (name.isEmpty()) continue;

                if (PaperValidator.isValidAuthor(name)) {
                    doc.getAuthors().add(new PaperAuthor(name, null, null, false));
                }
            }
        }

        if (!doc.getAuthors().isEmpty()) {
            doc.setConfidence("authors", 0.90);
        }
    }

    private static void extractAbstract(List<Zone> zones, JsonNode rules, PaperDocument doc) {
        if (rules == null) return;

        List<String> labels = new ArrayList<>();
        JsonNode labelsNode = rules.get("labels");
        if (labelsNode != null && labelsNode.isArray()) {
            for (JsonNode l : labelsNode) {
                labels.add(l.asText());
            }
        }

        for (Zone zone : zones) {
            if (zone.getType() != ZoneType.ABSTRACT) continue;
            String text = zone.getTextContent().trim();
            if (text.isEmpty()) continue;

            // Strip label prefix if present
            for (String label : labels) {
                // Case-insensitive prefix check
                if (text.toLowerCase().startsWith(label.toLowerCase())) {
                    text = text.substring(label.length()).trim();
                    // Strip leading colon or dash after label
                    if (text.startsWith(":") || text.startsWith("-") || text.startsWith("—")) {
                        text = text.substring(1).trim();
                    }
                    break;
                }
            }

            if (!text.isEmpty()) {
                doc.setAbstractText(text);
                doc.setConfidence("abstract", 0.92);
                break;
            }
        }
    }

    private static void extractKeywords(List<Zone> zones, JsonNode rules, PaperDocument doc) {
        if (rules == null) return;

        List<String> labels = new ArrayList<>();
        JsonNode labelsNode = rules.get("labels");
        if (labelsNode != null && labelsNode.isArray()) {
            for (JsonNode l : labelsNode) {
                labels.add(l.asText());
            }
        }

        List<String> separators = new ArrayList<>();
        JsonNode sepNode = rules.get("separators");
        if (sepNode != null && sepNode.isArray()) {
            for (JsonNode s : sepNode) {
                separators.add(s.asText());
            }
        }
        if (separators.isEmpty()) {
            separators.add(",");
        }

        for (Zone zone : zones) {
            if (zone.getType() != ZoneType.KEYWORDS) continue;
            String text = zone.getTextContent().trim();
            if (text.isEmpty()) continue;

            // Strip label prefix
            for (String label : labels) {
                if (text.toLowerCase().startsWith(label.toLowerCase())) {
                    text = text.substring(label.length()).trim();
                    if (text.startsWith(":") || text.startsWith("-") || text.startsWith("—")) {
                        text = text.substring(1).trim();
                    }
                    break;
                }
            }

            // Build separator regex
            StringBuilder sepRegex = new StringBuilder();
            for (String sep : separators) {
                if (sepRegex.length() > 0) sepRegex.append("|");
                sepRegex.append(Pattern.quote(sep));
            }

            String[] keywords = text.split(sepRegex.toString());
            for (String kw : keywords) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    doc.getKeywords().add(trimmed);
                }
            }

            if (!doc.getKeywords().isEmpty()) {
                doc.setConfidence("keywords", 0.90);
            }
            break;
        }
    }

    private static void extractReferences(List<Zone> zones, JsonNode rules, PaperDocument doc) {
        if (rules == null) return;

        String entryPattern = "bracket_number";
        JsonNode patNode = rules.get("entry_pattern");
        if (patNode != null) {
            entryPattern = patNode.asText();
        }

        StringBuilder allRefText = new StringBuilder();
        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.REFERENCE_BODY) {
                if (allRefText.length() > 0) allRefText.append("\n");
                allRefText.append(zone.getTextContent());
            }
        }
        if (allRefText.length() == 0) return;

        String refStr = allRefText.toString();
        String[] entries;

        if ("dot_number".equals(entryPattern)) {
            entries = DOT_NUMBER.split(refStr);
        } else {
            // bracket_number: split by [N]
            entries = Pattern.compile("(?=\\[\\d+\\])").split(refStr);
        }

        int refId = 0;
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            refId++;

            Matcher numMatcher = ENTRY_NUMBER.matcher(entry);
            int id = refId;
            String refText = entry;
            if (numMatcher.find()) {
                id = Integer.parseInt(numMatcher.group(1));
                refText = entry.substring(numMatcher.end()).trim();
            }

            PaperReference ref = new PaperReference(id, entry);
            parseReferenceFields(refText, ref);
            doc.getReferences().add(ref);
        }

        if (!doc.getReferences().isEmpty()) {
            doc.setConfidence("references", 0.88);
        }
    }

    private static void parseReferenceFields(String text, PaperReference ref) {
        int fieldsParsed = 0;

        // DOI
        Matcher doiMatcher = DOI_PATTERN.matcher(text);
        if (doiMatcher.find()) { ref.setDoi(doiMatcher.group()); fieldsParsed++; }

        // Year
        Matcher yearMatcher = YEAR_PATTERN.matcher(text);
        if (yearMatcher.find()) { ref.setYear(Integer.parseInt(yearMatcher.group(1))); fieldsParsed++; }

        // Title (quoted)
        Matcher titleMatcher = QUOTED_TITLE.matcher(text);
        if (titleMatcher.find()) {
            ref.setTitle(titleMatcher.group(1).trim());
            String beforeTitle = text.substring(0, titleMatcher.start()).trim();
            if (beforeTitle.endsWith(",")) beforeTitle = beforeTitle.substring(0, beforeTitle.length() - 1);
            if (!beforeTitle.isEmpty()) {
                ref.setAuthors(parseRefAuthors(beforeTitle));
                fieldsParsed += 2;
            } else {
                fieldsParsed++;
            }
            String afterTitle = text.substring(titleMatcher.end()).trim();
            if (afterTitle.startsWith(",")) afterTitle = afterTitle.substring(1).trim();
            extractVenue(afterTitle, ref);
            if (ref.getVenue() != null) fieldsParsed++;
        } else {
            int dotIdx = text.indexOf(". ");
            if (dotIdx > 0 && dotIdx < text.length() / 2) {
                String possibleAuthors = text.substring(0, dotIdx).trim();
                ref.setAuthors(parseRefAuthors(possibleAuthors));
                fieldsParsed++;
            }
        }

        ref.setConfidence(Math.min(1.0, fieldsParsed * 0.2));
    }

    private static List<String> parseRefAuthors(String text) {
        text = text.replaceAll("[,;.]+$", "").trim();
        String[] parts = text.split("\\s*[,;]\\s*(?:and\\s+|&\\s+)?|\\s+and\\s+|\\s*&\\s*");
        List<String> authors = new ArrayList<>();
        for (String part : parts) {
            part = part.replaceAll("[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡]", "").trim();
            if (!part.isEmpty() && part.length() > 1) authors.add(part);
        }
        return authors;
    }

    private static void extractVenue(String text, PaperReference ref) {
        text = text.replaceAll("(19|20)\\d{2}", "").trim();
        text = text.replaceAll("10\\.\\d{4,9}/\\S+", "").trim();
        text = text.replaceAll("pp?\\.?\\s*\\d+-\\d+", "").trim();
        text = text.replaceAll("[,;.]+$", "").trim();
        text = text.replaceAll("^[,;.]+", "").trim();
        if (!text.isEmpty() && text.length() > 2) ref.setVenue(text);
    }
}
