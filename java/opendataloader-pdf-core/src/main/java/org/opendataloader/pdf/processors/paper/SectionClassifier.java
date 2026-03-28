package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.*;

import java.util.*;
import java.util.regex.*;

public class SectionClassifier {

    private static final Map<String, String> SECTION_KEYWORDS = new LinkedHashMap<>();

    static {
        SECTION_KEYWORDS.put("introduction", "Introduction|서론|머리말");
        SECTION_KEYWORDS.put("related_work", "Related Work|Literature Review|관련 연구|선행 연구");
        SECTION_KEYWORDS.put("methods", "Method|Methodology|Approach|연구 방법|방법론|연구방법");
        SECTION_KEYWORDS.put("experiments", "Experiment|Evaluation|실험|평가");
        SECTION_KEYWORDS.put("results", "Results|결과|연구 결과|연구결과");
        SECTION_KEYWORDS.put("discussion", "Discussion|논의|고찰");
        SECTION_KEYWORDS.put("conclusion", "Conclusion|결론|맺음말");
        SECTION_KEYWORDS.put("acknowledgment", "Acknowledgment|Acknowledgement|감사의 글|사사");
        SECTION_KEYWORDS.put("appendix", "Appendix|부록");
    }

    public static void classify(List<Zone> zones, PaperDocument doc) {
        String currentHeading = null;
        String currentType = null;
        int currentPageStart = -1;
        int currentPageEnd = -1;
        StringBuilder currentContent = new StringBuilder();

        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.BODY_HEADING) {
                // Flush previous section if any
                if (currentHeading != null) {
                    doc.getSections().add(new PaperSection(
                            currentType, currentHeading,
                            currentContent.toString().trim(),
                            currentPageStart, currentPageEnd));
                }

                currentHeading = zone.getTextContent().trim();
                currentType = matchSectionType(currentHeading);
                currentPageStart = zone.getPageNumber();
                currentPageEnd = zone.getPageNumber();
                currentContent = new StringBuilder();
            } else if (zone.getType() == ZoneType.BODY_TEXT && currentHeading != null) {
                String text = zone.getTextContent().trim();
                if (!text.isEmpty()) {
                    if (currentContent.length() > 0) {
                        currentContent.append("\n");
                    }
                    currentContent.append(text);
                    currentPageEnd = zone.getPageNumber();
                }
            }
        }

        // Flush last section
        if (currentHeading != null) {
            doc.getSections().add(new PaperSection(
                    currentType, currentHeading,
                    currentContent.toString().trim(),
                    currentPageStart, currentPageEnd));
        }
    }

    static String stripNumberPrefix(String text) {
        return text.replaceFirst("^\\s*\\d+[.)]?\\s*", "");
    }

    static String matchSectionType(String headingText) {
        String stripped = stripNumberPrefix(headingText).trim();
        for (Map.Entry<String, String> entry : SECTION_KEYWORDS.entrySet()) {
            Pattern p = Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE);
            if (p.matcher(stripped).find()) {
                return entry.getKey();
            }
        }
        return "other";
    }
}
