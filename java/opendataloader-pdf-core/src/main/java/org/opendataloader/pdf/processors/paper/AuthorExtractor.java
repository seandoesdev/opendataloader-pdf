package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.*;

import java.util.*;
import java.util.regex.*;

public class AuthorExtractor {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+");
    private static final Pattern SUPERSCRIPT_MARKER = Pattern.compile("[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡§]");
    private static final Pattern AFFILIATION_LINE = Pattern.compile("^\\s*([¹²³⁴⁵⁶⁷⁸⁹⁰*†‡§]+)\\s*(.+)$");
    private static final Pattern CORRESPONDING = Pattern.compile(
            "(\\*|corresponding\\s+author|교신저자)", Pattern.CASE_INSENSITIVE);

    public static void extract(List<Zone> zones, PaperDocument doc) {
        StringBuilder authorText = new StringBuilder();
        double totalConfidence = 0;
        int count = 0;

        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.AUTHOR_BLOCK) {
                String text = zone.getTextContent().trim();
                if (text.isEmpty()) continue;
                if (authorText.length() > 0) {
                    authorText.append("\n");
                }
                authorText.append(text);
                totalConfidence += zone.getConfidence();
                count++;
            }
        }

        if (count == 0) return;

        String fullText = authorText.toString();

        // Extract all emails from the full text
        List<String> emails = new ArrayList<>();
        Matcher emailMatcher = EMAIL_PATTERN.matcher(fullText);
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }

        // Build affiliation map from lines like "¹Seoul National University"
        Map<String, String> affiliationMap = new LinkedHashMap<>();
        String[] allLines = fullText.split("\n");
        List<String> nameLines = new ArrayList<>();

        for (String line : allLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher affMatcher = AFFILIATION_LINE.matcher(trimmed);
            // A line is an affiliation line if it starts with a marker and the rest
            // does not look like a name list (heuristic: affiliation lines are longer
            // and don't have commas separating short tokens with markers)
            if (affMatcher.matches() && !looksLikeNameList(trimmed)) {
                String markers = affMatcher.group(1);
                String affText = affMatcher.group(2).trim();
                for (int i = 0; i < markers.length(); i++) {
                    String marker = String.valueOf(markers.charAt(i));
                    affiliationMap.put(marker, affText);
                }
            } else {
                nameLines.add(trimmed);
            }
        }

        // Parse author names from name lines
        List<PaperAuthor> authors = new ArrayList<>();
        for (String nameLine : nameLines) {
            // Remove emails from the name line for cleaner parsing
            String cleanLine = EMAIL_PATTERN.matcher(nameLine).replaceAll("").trim();
            // Remove "corresponding author" text
            cleanLine = cleanLine.replaceAll("(?i)corresponding\\s+author", "").trim();
            cleanLine = cleanLine.replaceAll("교신저자", "").trim();

            // Split by comma
            String[] parts = cleanLine.split(",");
            for (String part : parts) {
                String name = part.trim();
                if (name.isEmpty()) continue;

                // Check for corresponding marker
                boolean isCorresponding = CORRESPONDING.matcher(name).find();

                // Extract superscript markers from the name
                List<String> markers = new ArrayList<>();
                Matcher markerMatcher = SUPERSCRIPT_MARKER.matcher(name);
                while (markerMatcher.find()) {
                    markers.add(markerMatcher.group());
                }

                // Clean name of markers
                name = SUPERSCRIPT_MARKER.matcher(name).replaceAll("").trim();
                if (name.isEmpty()) continue;

                // Look up affiliation
                String affiliation = null;
                for (String marker : markers) {
                    if (!"*".equals(marker) && affiliationMap.containsKey(marker)) {
                        affiliation = affiliationMap.get(marker);
                        break;
                    }
                }

                // Find email for this author (simple: assign in order)
                String email = null;
                // Try to match email by name heuristic (first part of email matches
                // first letter of name)
                // Fallback: assign emails in order
                if (!emails.isEmpty()) {
                    email = emails.remove(0);
                }

                if (isCorresponding || markers.contains("*")) {
                    isCorresponding = true;
                }

                authors.add(new PaperAuthor(name, affiliation, email, isCorresponding));
            }
        }

        doc.getAuthors().addAll(authors);
        doc.setConfidence("authors", totalConfidence / count);
    }

    private static boolean looksLikeNameList(String text) {
        // If the text (after the marker) contains multiple comma-separated tokens
        // with markers, it's likely a name list
        String afterMarker = text.replaceFirst("^\\s*[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡§]+\\s*", "");
        // If remaining text has markers interspersed, it's names
        Matcher m = SUPERSCRIPT_MARKER.matcher(afterMarker);
        int markerCount = 0;
        while (m.find()) markerCount++;
        return markerCount > 0;
    }
}
