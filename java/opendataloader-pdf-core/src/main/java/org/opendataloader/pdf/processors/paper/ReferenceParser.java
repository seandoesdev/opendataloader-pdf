package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.*;
import java.util.*;
import java.util.regex.*;

public class ReferenceParser {
    private static final Pattern ENTRY_SPLIT = Pattern.compile("(?m)(?=^\\s*\\[\\d+\\]|^\\s*\\d+[.)]+\\s)");
    private static final Pattern ENTRY_NUMBER = Pattern.compile("^\\s*\\[?(\\d+)[.)\\]]?\\s*");
    private static final Pattern QUOTED_TITLE = Pattern.compile("[\"\u201c](.+?)[\"\u201d]");
    private static final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");

    public static void parse(List<Zone> zones, PaperDocument doc) {
        StringBuilder allRefText = new StringBuilder();
        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.REFERENCE_BODY) {
                if (allRefText.length() > 0) allRefText.append("\n");
                allRefText.append(zone.getTextContent());
            }
        }
        if (allRefText.length() == 0) return;

        String[] entries = ENTRY_SPLIT.split(allRefText.toString());
        int refId = 0;
        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            refId++;

            // Strip entry number
            Matcher numMatcher = ENTRY_NUMBER.matcher(entry);
            int id = refId;
            String refText = entry;
            if (numMatcher.find()) {
                id = Integer.parseInt(numMatcher.group(1));
                refText = entry.substring(numMatcher.end()).trim();
            }

            PaperReference ref = new PaperReference(id, entry);
            parseFields(refText, ref);
            doc.getReferences().add(ref);
        }
    }

    private static void parseFields(String text, PaperReference ref) {
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
            // Authors = text before quote
            String beforeTitle = text.substring(0, titleMatcher.start()).trim();
            if (beforeTitle.endsWith(",")) beforeTitle = beforeTitle.substring(0, beforeTitle.length() - 1);
            if (!beforeTitle.isEmpty()) {
                ref.setAuthors(parseAuthors(beforeTitle));
                fieldsParsed += 2; // authors + title
            } else {
                fieldsParsed++;
            }
            // Venue = text after title, before year
            String afterTitle = text.substring(titleMatcher.end()).trim();
            if (afterTitle.startsWith(",")) afterTitle = afterTitle.substring(1).trim();
            extractVenue(afterTitle, ref);
            if (ref.getVenue() != null) fieldsParsed++;
        } else {
            // No quoted title -- try comma-based splitting
            // First segment before first period might be authors
            int dotIdx = text.indexOf(". ");
            if (dotIdx > 0 && dotIdx < text.length() / 2) {
                String possibleAuthors = text.substring(0, dotIdx).trim();
                ref.setAuthors(parseAuthors(possibleAuthors));
                fieldsParsed++;
            }
        }

        ref.setConfidence(Math.min(1.0, fieldsParsed * 0.2));
    }

    private static List<String> parseAuthors(String text) {
        // Remove trailing punctuation
        text = text.replaceAll("[,;.]+$", "").trim();
        String[] parts = text.split("\\s*[,;]\\s*(?:and\\s+|&\\s+)?|\\s+and\\s+|\\s*&\\s*");
        List<String> authors = new ArrayList<>();
        for (String part : parts) {
            part = part.replaceAll("[\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079\u2070*\u2020\u2021]", "").trim();
            if (!part.isEmpty() && part.length() > 1) authors.add(part);
        }
        return authors;
    }

    private static void extractVenue(String text, PaperReference ref) {
        // Remove year, doi, page numbers
        text = text.replaceAll("(19|20)\\d{2}", "").trim();
        text = text.replaceAll("10\\.\\d{4,9}/\\S+", "").trim();
        text = text.replaceAll("pp?\\.?\\s*\\d+-\\d+", "").trim();
        text = text.replaceAll("[,;.]+$", "").trim();
        text = text.replaceAll("^[,;.]+", "").trim();
        if (!text.isEmpty() && text.length() > 2) ref.setVenue(text);
    }
}
