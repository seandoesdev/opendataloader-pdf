package org.opendataloader.pdf.processors.paper;

import java.util.Set;

public class PaperValidator {

    private static final Set<String> CATEGORY_LABELS = Set.of(
        "Original Article", "Review Article", "Case Report", "Brief Report",
        "Short Communication", "Research Article", "Letter to the Editor",
        "Editorial", "Commentary", "Erratum", "Corrigendum",
        "원저", "종설", "증례보고", "단보");

    public static boolean isValidTitle(String title) {
        if (title == null || title.trim().length() < 5) return false;
        if (title.length() > 500) return false;
        if (CATEGORY_LABELS.contains(title.trim())) return false;
        return true;
    }

    public static boolean isValidAuthor(String name) {
        if (name == null || name.trim().length() < 2 || name.length() > 100) return false;
        if (name.matches(".*\\b(Received|Revised|Accepted|Published|Submitted)\\b.*")) return false;
        if (name.matches(".*\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\b.*")) return false;
        if (name.matches("^[\\d\\s.,;:]+$")) return false;
        if (name.matches("^\\d{4}\\..*")) return false;
        return true;
    }

    public static boolean isValidDoi(String doi) {
        return doi != null && doi.matches("10\\.\\d{4,9}/\\S+");
    }

    public static boolean isValidAbstract(String text) {
        return text != null && text.trim().length() >= 50;
    }

    public static boolean isValidYear(Integer year) {
        return year != null && year >= 1900 && year <= java.time.Year.now().getValue() + 2;
    }
}
