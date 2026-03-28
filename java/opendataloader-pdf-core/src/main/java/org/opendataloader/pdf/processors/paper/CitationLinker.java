package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.*;
import java.util.*;
import java.util.regex.*;

public class CitationLinker {
    private static final Pattern NUMERIC_CITATION = Pattern.compile("\\[(\\d+(?:\\s*[,\\-\u2013]\\s*\\d+)*)\\]");
    private static final Pattern AUTHOR_YEAR_CITATION = Pattern.compile(
        "\\(([A-Za-z\uAC00-\uD7A3]+(?:\\s+(?:et\\s+al\\.|\\uC678))?,?\\s*\\d{4})\\)");

    public static void link(List<Zone> zones, PaperDocument doc) {
        Map<Integer, PaperReference> refMap = new HashMap<>();
        for (PaperReference ref : doc.getReferences()) {
            refMap.put(ref.getId(), ref);
        }

        for (Zone zone : zones) {
            if (zone.getType() != ZoneType.BODY_TEXT && zone.getType() != ZoneType.BODY_HEADING) continue;
            String text = zone.getTextContent();
            int page = zone.getPageNumber();

            // Numeric citations
            Matcher numMatcher = NUMERIC_CITATION.matcher(text);
            while (numMatcher.find()) {
                String group = numMatcher.group(1);
                List<Integer> ids = expandCitationRange(group);
                String context = extractContext(text, numMatcher.start(), numMatcher.end());
                for (int id : ids) {
                    CitationLink link = new CitationLink(page, context);
                    PaperReference ref = refMap.get(id);
                    if (ref != null) {
                        ref.addCitation(link);
                    } else {
                        doc.getUnlinkedCitations().add(link);
                    }
                }
            }

            // Author-year citations
            Matcher ayMatcher = AUTHOR_YEAR_CITATION.matcher(text);
            while (ayMatcher.find()) {
                String context = extractContext(text, ayMatcher.start(), ayMatcher.end());
                // Try to match by year to a reference
                Matcher yearInCit = Pattern.compile("(\\d{4})").matcher(ayMatcher.group(1));
                boolean linked = false;
                if (yearInCit.find()) {
                    int year = Integer.parseInt(yearInCit.group(1));
                    for (PaperReference ref : doc.getReferences()) {
                        if (ref.getYear() != null && ref.getYear() == year) {
                            ref.addCitation(new CitationLink(page, context));
                            linked = true;
                            break;
                        }
                    }
                }
                if (!linked) doc.getUnlinkedCitations().add(new CitationLink(page, context));
            }
        }
    }

    static List<Integer> expandCitationRange(String group) {
        List<Integer> ids = new ArrayList<>();
        String[] parts = group.split("\\s*,\\s*");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-") || part.contains("\u2013")) {
                String[] range = part.split("\\s*[-\u2013]\\s*");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end; i++) ids.add(i);
                    } catch (NumberFormatException e) { /* skip */ }
                }
            } else {
                try { ids.add(Integer.parseInt(part)); } catch (NumberFormatException e) { /* skip */ }
            }
        }
        return ids;
    }

    private static String extractContext(String text, int start, int end) {
        int ctxStart = Math.max(0, start - 30);
        int ctxEnd = Math.min(text.length(), end + 30);
        return "..." + text.substring(ctxStart, ctxEnd) + "...";
    }
}
