package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts paper metadata by directly scanning the original IObject list.
 *
 * This extractor does NOT depend on ZoneClassifier results. Instead, it uses
 * the raw PDF extraction output (headings, paragraphs, font sizes, positions)
 * with journal-specific template rules to identify each section.
 *
 * This approach is robust across 200+ different journal layouts because:
 * 1. IObject types (heading/paragraph) are reliably detected by the base pipeline
 * 2. Font sizes are exact values from the PDF
 * 3. Templates only configure WHAT labels to look for, not HOW to classify zones
 */
public class TemplateBasedExtractor {

    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");
    private static final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");
    private static final Pattern QUOTED_TITLE = Pattern.compile("[\"\u201c](.+?)[\"\u201d]");
    private static final Pattern ENTRY_NUMBER = Pattern.compile("^\\s*\\[?(\\d+)[.)\\]]?\\s*");
    private static final Pattern TRAILING_NUMBERS = Pattern.compile("[\\d]+$");
    private static final Pattern SECTION_NUM = Pattern.compile(
        "^\\s*([IVXⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩivx]+\\.|\\d+\\.?)\\s");

    /**
     * Extract paper metadata from raw IObject contents using template rules.
     *
     * @param contents  page-indexed IObject lists from the base pipeline
     * @param zones     zone list (used only for fallback/supplementary data)
     * @param template  journal-specific template JSON
     * @param doc       output document to populate
     */
    public static void extract(List<List<IObject>> contents, List<Zone> zones,
                                JsonNode template, PaperDocument doc) {
        if (template == null || contents.isEmpty()) return;

        // Flatten all IObjects with metadata
        List<Element> elements = flattenElements(contents);
        if (elements.isEmpty()) return;

        // Compute body font size (most common font size = body text)
        double bodyFontSize = computeBodyFontSize(elements);

        extractTitle(elements, bodyFontSize, template.get("title_rules"), doc);
        extractAuthors(elements, bodyFontSize, template.get("author_rules"), doc);

        // Fallback: if IObject-based author extraction found nothing, try zone-based
        // extraction using spatial coordinates (handles split-layout PDFs where IObject
        // order doesn't match visual layout)
        if (doc.getAuthors().isEmpty() && zones != null && !zones.isEmpty()) {
            extractAuthorsFromZones(zones, bodyFontSize, template.get("author_rules"), doc);
        }

        extractAbstract(elements, template.get("abstract_rules"), template.get("keyword_rules"), doc);
        extractKeywords(elements, template.get("keyword_rules"), doc);
        extractSections(elements, doc);
        extractReferences(elements, template.get("reference_rules"), doc);
    }

    /** Backward-compatible: extract from zones only (used by tests) */
    public static void extract(List<Zone> zones, JsonNode template, PaperDocument doc) {
        if (template == null || zones.isEmpty()) return;
        // Convert zones to element-like structure and use zone text
        List<Element> elements = new ArrayList<>();
        for (Zone z : zones) {
            String text = z.getTextContent().trim();
            if (text.isEmpty()) continue;
            boolean isHeading = z.getFeatures().isHeading()
                || (z.getFeatures().isBold() && z.getFeatures().getFontSizeRatio() > 1.2);
            elements.add(new Element(text, z.getFeatures().getMaxFontSize(),
                z.getFeatures().isBold(), isHeading, z.getPageNumber(),
                z.getFeatures().getFontSizeRatio()));
        }
        double bodyFontSize = computeBodyFontSize(elements);
        extractTitle(elements, bodyFontSize, template.get("title_rules"), doc);
        extractAuthors(elements, bodyFontSize, template.get("author_rules"), doc);
        extractAbstract(elements, template.get("abstract_rules"), template.get("keyword_rules"), doc);
        extractKeywords(elements, template.get("keyword_rules"), doc);
        extractSections(elements, doc);
        extractReferences(elements, template.get("reference_rules"), doc);
    }

    // =========================================================================
    // Internal element model — wraps IObject data for uniform access
    // =========================================================================

    static class Element {
        final String text;
        final double fontSize;
        final boolean bold;
        final boolean heading;
        final int page;          // 1-based
        final double fontRatio;  // fontSize / bodyFontSize

        Element(String text, double fontSize, boolean bold, boolean heading, int page, double fontRatio) {
            this.text = text;
            this.fontSize = fontSize;
            this.bold = bold;
            this.heading = heading;
            this.page = page;
            this.fontRatio = fontRatio;
        }
    }

    /** Flatten IObject tree into a sequential list of text elements */
    private static List<Element> flattenElements(List<List<IObject>> contents) {
        List<Element> elements = new ArrayList<>();
        for (int pageIdx = 0; pageIdx < contents.size(); pageIdx++) {
            int pageNum = pageIdx + 1;
            for (IObject obj : contents.get(pageIdx)) {
                addElements(obj, pageNum, elements);
            }
        }
        return elements;
    }

    private static void addElements(IObject obj, int pageNum, List<Element> out) {
        if (obj instanceof SemanticHeading) {
            String text = getTextValue(obj);
            double fontSize = getMaxFontSize(obj);
            boolean bold = isBold(obj);
            if (!text.trim().isEmpty()) {
                out.add(new Element(text.trim(), fontSize, bold, true, pageNum, 0));
            }
        } else if (obj instanceof SemanticTextNode) {
            SemanticTextNode tn = (SemanticTextNode) obj;
            String text = tn.getValue();
            if (text != null && !text.trim().isEmpty()) {
                out.add(new Element(text.trim(), tn.getFontSize(), tn.getFontWeight() >= 700,
                    false, pageNum, 0));
            }
        } else if (obj instanceof SemanticParagraph) {
            String text = getTextValue(obj);
            double fontSize = getMaxFontSize(obj);
            boolean bold = isBold(obj);
            if (!text.trim().isEmpty()) {
                out.add(new Element(text.trim(), fontSize, bold, false, pageNum, 0));
            }
        }
        // Skip images, footers, headers, lists for paper metadata
    }

    private static String getTextValue(IObject obj) {
        if (obj instanceof SemanticTextNode) {
            return ((SemanticTextNode) obj).getValue();
        }
        StringBuilder sb = new StringBuilder();
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                String childText = getTextValue(child);
                if (childText != null && !childText.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(childText);
                }
            }
        }
        return sb.toString();
    }

    private static double getMaxFontSize(IObject obj) {
        if (obj instanceof SemanticTextNode) return ((SemanticTextNode) obj).getFontSize();
        double max = 0;
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                max = Math.max(max, getMaxFontSize(child));
            }
        }
        return max;
    }

    private static boolean isBold(IObject obj) {
        if (obj instanceof SemanticTextNode) return ((SemanticTextNode) obj).getFontWeight() >= 700;
        if (obj instanceof INode) {
            for (INode child : ((INode) obj).getChildren()) {
                if (isBold(child)) return true;
            }
        }
        return false;
    }

    /** Find the most common font size (= body text) */
    private static double computeBodyFontSize(List<Element> elements) {
        Map<Integer, Integer> fontCounts = new HashMap<>();
        for (Element e : elements) {
            if (e.fontSize > 0 && !e.heading) {
                int key = (int) (e.fontSize * 10); // round to 0.1
                fontCounts.merge(key, e.text.length(), Integer::sum);
            }
        }
        int bestKey = 100;
        int bestCount = 0;
        for (var entry : fontCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestKey = entry.getKey();
            }
        }
        return bestKey / 10.0;
    }

    // =========================================================================
    // TITLE: Find the largest-font heading on page 1, skip category labels
    // =========================================================================

    private static void extractTitle(List<Element> elements, double bodyFontSize,
                                      JsonNode rules, PaperDocument doc) {
        Set<String> skipLabels = getStringSet(rules, "skip_labels");
        skipLabels.addAll(Set.of("original article", "review article", "case report",
            "brief report", "short communication", "research article"));

        Element bestTitle = null;
        double bestFontSize = 0;
        int bestTitleIdx = -1;

        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (e.page > 2) break; // Only check first 2 pages
            if (e.text.length() < 5) continue;
            if (skipLabels.contains(e.text.toLowerCase().trim())) continue;
            if (!PaperValidator.isValidTitle(e.text)) continue;

            // Title should be significantly larger than body text
            if (e.fontSize <= bodyFontSize * 1.2) continue;

            if (e.fontSize > bestFontSize) {
                bestFontSize = e.fontSize;
                bestTitle = e;
                bestTitleIdx = i;
            }
        }

        if (bestTitle != null) {
            doc.setTitle(bestTitle.text);
            doc.setTitleElementIndex(bestTitleIdx);
            doc.setConfidence("title", 0.92);
            boolean hasKorean = bestTitle.text.codePoints().anyMatch(cp ->
                (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0x3131 && cp <= 0x318E));
            doc.setLanguage(hasKorean ? "ko" : "en");
        }
    }

    // =========================================================================
    // AUTHORS: Short paragraphs between title and abstract on page 1
    // =========================================================================

    private static final Pattern EXCLUDE_DEFAULT = Pattern.compile(
        "(Received|Revised|Accepted|Published|Submitted|투고|심사|게재)", Pattern.CASE_INSENSITIVE);
    /** Matches trailing affiliation block: last author marker(s) followed by affiliation text.
     *  e.g., "정화식***** *첨단우암병원 작업치료사 **순천향대..." →  captures up to "정화식*****"
     *  Group 1 = everything before the affiliation block (the author names). */
    private static final Pattern AFFILIATION_BLOCK = Pattern.compile(
        "(.*[*†‡§]+)\\s+[*†‡§]*[\\p{IsHangul}\\p{IsLatin}].*$");
    private static final Pattern ABSTRACT_LABEL = Pattern.compile(
        "^\\s*(Abstract|ABSTRACT|Purpose|Background|Objectives|초록|요약|국문\\s*초록|요\\s*약)",
        Pattern.CASE_INSENSITIVE);

    private static void extractAuthors(List<Element> elements, double bodyFontSize,
                                        JsonNode rules, PaperDocument doc) {
        if (doc.getTitle() == null) return;

        List<Pattern> excludePatterns = getPatternList(rules, "exclude_patterns");
        excludePatterns.add(EXCLUDE_DEFAULT);

        String separator = rules != null && rules.has("separator")
            ? rules.get("separator").asText() : ",";

        // Phase 1: Use title element index recorded by extractTitle
        int titleIdx = doc.getTitleElementIndex();
        double titleFontSize = titleIdx >= 0 && titleIdx < elements.size()
            ? elements.get(titleIdx).fontSize : 0;
        if (titleIdx < 0) return;

        // Phase 2: Collect candidate author text fragments between title and abstract
        StringBuilder authorTextBuilder = new StringBuilder();
        for (int i = titleIdx + 1; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (e.page > 1) break; // Authors are on page 1

            // STOP: hit abstract label
            if (ABSTRACT_LABEL.matcher(e.text).find()) break;

            // STOP: hit a heading that looks like a section (not a subtitle)
            // Subtitles are headings right after title with smaller font — skip them
            if (e.heading && e.fontSize > bodyFontSize && e.fontSize >= titleFontSize * 0.9) break;
            // Skip subtitle headings (smaller than title, right after it)
            if (e.heading && e.fontSize < titleFontSize * 0.9) continue;

            // STOP: long text = abstract/body, not author names
            // Author lines with many co-authors and affiliation markers can exceed 150 chars,
            // so use a higher threshold; sentence-pattern check below catches abstracts.
            if (e.text.length() > 300) break;

            // STOP: text looks like a paragraph (multiple sentences)
            if (e.text.contains(". ") && e.text.length() > 80) break;

            // SKIP: date/submission patterns
            boolean excluded = false;
            for (Pattern ep : excludePatterns) {
                if (ep.matcher(e.text).find()) { excluded = true; break; }
            }
            if (excluded) continue;

            // SKIP: very small font (footnotes)
            if (e.fontSize > 0 && e.fontSize < bodyFontSize * 0.6) continue;

            // SKIP: copyright, URLs, TOC markers, non-name patterns
            if (e.text.startsWith("©") || e.text.startsWith("http")) continue;
            if (e.text.startsWith("<") || e.text.startsWith("|")) continue;
            if (e.text.matches("^\\s*(차\\s*례|목\\s*차|Table of Contents).*")) continue;

            // Accumulate candidate fragments with separator so they can be parsed together
            if (authorTextBuilder.length() > 0) {
                authorTextBuilder.append(separator);
            }
            authorTextBuilder.append(e.text);
        }

        // Phase 3: Strip trailing affiliation block before parsing author names.
        // Pattern: last author's marker (*****) followed by affiliation text
        // e.g., "정화식***** *첨단우암병원 작업치료사 **순천향대학병원..."
        // We cut at the point where a marker is followed by non-marker text (affiliation).
        String authorText = authorTextBuilder.toString();
        authorText = AFFILIATION_BLOCK.matcher(authorText).replaceFirst("$1");

        // Phase 4: Parse all collected author text at once
        if (!authorText.isEmpty()) {
            String[] parts = authorText.split(Pattern.quote(separator));
            for (String part : parts) {
                String name = cleanAuthorName(part);
                if (name.isEmpty() || name.length() > 30) continue;
                if (!PaperValidator.isValidAuthor(name)) continue;
                doc.getAuthors().add(new PaperAuthor(name, null, null,
                    part.contains("*") || part.contains("†")));
            }
        }

        if (!doc.getAuthors().isEmpty()) {
            doc.setConfidence("authors", 0.90);
        }
    }

    // =========================================================================
    // AUTHORS FALLBACK: Zone-based extraction using spatial coordinates
    // Handles split-layout PDFs where IObject order doesn't match visual layout
    // =========================================================================

    /** Pattern matching abstract labels including those preceded by brackets like 〈국문 요약〉 */
    private static final Pattern ABSTRACT_LABEL_LOOSE = Pattern.compile(
        "(Abstract|ABSTRACT|Purpose|Background|Objectives|초록|요약|국문\\s*초록|국문\\s*요약)",
        Pattern.CASE_INSENSITIVE);

    /** Pattern matching common non-author structural elements */
    private static final Pattern STRUCTURAL_LABEL = Pattern.compile(
        "(차\\s*례|목\\s*차|Table of Contents|주제어|Keywords|Key\\s*words|참고\\s*문헌|References)",
        Pattern.CASE_INSENSITIVE);

    private static void extractAuthorsFromZones(List<Zone> zones, double bodyFontSize,
                                                 JsonNode rules, PaperDocument doc) {
        if (doc.getTitle() == null) return;

        String separator = rules != null && rules.has("separator")
            ? rules.get("separator").asText() : ",";

        // Optional spatial hint: only consider zones in the left portion of the page
        double xMaxRatio = rules != null && rules.has("x_max_ratio")
            ? rules.get("x_max_ratio").asDouble(1.0) : 1.0;

        List<Pattern> excludePatterns = getPatternList(rules, "exclude_patterns");
        excludePatterns.add(EXCLUDE_DEFAULT);

        // Find title zone to establish vertical reference
        Zone titleZone = null;
        for (Zone z : zones) {
            if (z.getPageNumber() <= 1 && z.getTextContent().trim().equals(doc.getTitle())) {
                titleZone = z;
                break;
            }
        }
        if (titleZone == null) return;

        double titleBottomY = titleZone.getBounds().getBottomY();

        // Find max rightX among all page-1 zones to estimate page width
        double pageWidth = 0;
        for (Zone z : zones) {
            if (z.getPageNumber() <= 1 && z.getBounds().getRightX() > pageWidth) {
                pageWidth = z.getBounds().getRightX();
            }
        }

        // Sort page-1 zones by y-coordinate (top to bottom) for correct ordering
        List<Zone> page1Zones = new ArrayList<>();
        for (Zone z : zones) {
            if (z.getPageNumber() <= 1) page1Zones.add(z);
        }
        page1Zones.sort((a, b) -> Double.compare(b.getBounds().getTopY(), a.getBounds().getTopY()));

        // Collect candidate author zones: below title, above abstract, short text
        StringBuilder authorTextBuilder = new StringBuilder();
        for (Zone z : page1Zones) {
            String text = z.getTextContent().trim();
            if (text.isEmpty()) continue;

            // Spatial filter: zone must be below the title
            if (z.getBounds().getTopY() >= titleBottomY) continue;

            // Spatial filter: x_max_ratio — only consider left portion of page
            if (xMaxRatio < 1.0 && pageWidth > 0) {
                if (z.getBounds().getLeftX() > pageWidth * xMaxRatio) continue;
            }

            // STOP: hit abstract label (loose match handles 〈국문 요약〉 etc.)
            if (ABSTRACT_LABEL_LOOSE.matcher(text).find()) break;

            // SKIP: structural labels (차례, 키워드, 참고문헌 etc.)
            if (STRUCTURAL_LABEL.matcher(text).find()) continue;

            // SKIP: long text (body paragraphs)
            if (text.length() > 150) continue;
            if (text.contains(". ") && text.length() > 80) continue;

            // SKIP: excluded patterns (Received, Revised etc.)
            boolean excluded = false;
            for (Pattern ep : excludePatterns) {
                if (ep.matcher(text).find()) { excluded = true; break; }
            }
            if (excluded) continue;

            // SKIP: very small font (footnotes)
            if (z.getFeatures().getMaxFontSize() > 0
                && z.getFeatures().getMaxFontSize() < bodyFontSize * 0.6) continue;

            // SKIP: non-name patterns
            if (text.startsWith("©") || text.startsWith("http")) continue;
            if (text.startsWith("<") || text.startsWith("|")) continue;
            if (text.matches("^[-–—\\d\\s]+$")) continue; // page numbers like "- 1 -"

            if (authorTextBuilder.length() > 0) {
                authorTextBuilder.append(separator);
            }
            authorTextBuilder.append(text);
        }

        // Strip trailing affiliation block and parse
        String authorText = authorTextBuilder.toString();
        authorText = AFFILIATION_BLOCK.matcher(authorText).replaceFirst("$1");

        if (!authorText.isEmpty()) {
            String[] parts = authorText.split(Pattern.quote(separator));
            for (String part : parts) {
                String name = cleanAuthorName(part);
                if (name.isEmpty() || name.length() > 30) continue;
                if (!PaperValidator.isValidAuthor(name)) continue;
                doc.getAuthors().add(new PaperAuthor(name, null, null,
                    part.contains("*") || part.contains("†")));
            }
        }

        if (!doc.getAuthors().isEmpty()) {
            doc.setConfidence("authors", 0.85); // Slightly lower confidence for fallback
        }
    }

    private static String cleanAuthorName(String raw) {
        String name = raw.trim();
        // Strip superscript markers
        name = name.replaceAll("[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡§]", "").trim();
        // Strip trailing regular numbers (e.g., "Chae1" → "Chae")
        name = TRAILING_NUMBERS.matcher(name).replaceAll("").trim();
        // Strip leading "N)" pattern (e.g., "1)" → "")
        name = name.replaceAll("^\\d+\\)", "").trim();
        // Strip parenthetical content (affiliations, titles)
        name = name.replaceAll("\\([^)]*\\)", "").trim();
        name = name.replaceAll("\\([^)]*$", "").trim();
        // Strip common Korean academic titles
        name = name.replaceAll("(교수|박사|강사|연구원|교수\\)|박사\\))$", "").trim();
        // Strip trailing punctuation
        name = name.replaceAll("[),:;.]+$", "").trim();
        // Strip leading punctuation
        name = name.replaceAll("^[(<:;.]+", "").trim();
        return name;
    }

    // =========================================================================
    // ABSTRACT: Find abstract label, collect text until keyword label or heading
    // =========================================================================

    private static void extractAbstract(List<Element> elements,
                                         JsonNode abstractRules, JsonNode keywordRules,
                                         PaperDocument doc) {
        List<String> labels = getStringList(abstractRules, "labels");
        if (labels.isEmpty()) {
            labels = List.of("Abstract", "ABSTRACT", "Purpose", "Background",
                "Objectives", "초록", "요약", "국문초록", "국문 초록");
        }
        List<String> endLabels = getStringList(keywordRules != null ? keywordRules : abstractRules, "labels");
        List<String> defaultEndLabels = List.of("Keywords", "Key words", "KEYWORDS",
            "키워드", "핵심어", "주제어", "주요어");

        StringBuilder abstractText = new StringBuilder();
        boolean collecting = false;

        for (Element e : elements) {
            if (!collecting) {
                for (String label : labels) {
                    if (e.text.toLowerCase().startsWith(label.toLowerCase())) {
                        String after = e.text.substring(label.length()).trim();
                        if (after.startsWith(":") || after.startsWith("-") || after.startsWith("—")) {
                            after = after.substring(1).trim();
                        }
                        // Check if keywords are in the same element
                        String beforeKw = extractBeforeKeywordLabel(after, defaultEndLabels);
                        if (beforeKw != null) {
                            abstractText.append(beforeKw);
                        } else if (!after.isEmpty()) {
                            abstractText.append(after);
                            collecting = true;
                        } else {
                            collecting = true; // label is standalone, text follows
                        }
                        break;
                    }
                }
            } else {
                // Stop at keyword label
                boolean isEnd = false;
                for (String endLabel : defaultEndLabels) {
                    if (e.text.toLowerCase().startsWith(endLabel.toLowerCase())) {
                        isEnd = true;
                        break;
                    }
                }
                if (isEnd) break;

                // Stop at heading
                if (e.heading && e.fontSize > 0) break;

                if (abstractText.length() > 0) abstractText.append(" ");
                abstractText.append(e.text);
            }
        }

        if (abstractText.length() > 0) {
            String result = abstractText.toString().trim();
            if (PaperValidator.isValidAbstract(result)) {
                doc.setAbstractText(result);
                doc.setConfidence("abstract", 0.92);
            }
        }
    }

    private static String extractBeforeKeywordLabel(String text, List<String> endLabels) {
        for (String label : endLabels) {
            int idx = text.toLowerCase().indexOf(label.toLowerCase());
            if (idx > 0) return text.substring(0, idx).trim();
        }
        return null;
    }

    // =========================================================================
    // KEYWORDS: Find keyword label, extract and split
    // =========================================================================

    private static void extractKeywords(List<Element> elements, JsonNode rules, PaperDocument doc) {
        List<String> labels = getStringList(rules, "labels");
        if (labels.isEmpty()) {
            labels = List.of("Keywords", "Key words", "KEYWORDS", "키워드",
                "핵심어", "주제어", "주요어");
        }
        List<String> separators = getStringList(rules, "separators");
        if (separators.isEmpty()) separators = List.of(",", ";");

        for (Element e : elements) {
            for (String label : labels) {
                int idx = e.text.toLowerCase().indexOf(label.toLowerCase());
                if (idx >= 0) {
                    String afterLabel = e.text.substring(idx + label.length()).trim();
                    if (afterLabel.startsWith(":") || afterLabel.startsWith("-")) {
                        afterLabel = afterLabel.substring(1).trim();
                    }
                    if (afterLabel.isEmpty()) continue;

                    StringBuilder sepRegex = new StringBuilder();
                    for (String sep : separators) {
                        if (sepRegex.length() > 0) sepRegex.append("|");
                        sepRegex.append(Pattern.quote(sep));
                    }

                    String[] keywords = afterLabel.split(sepRegex.toString());
                    for (String kw : keywords) {
                        String trimmed = kw.trim();
                        if (!trimmed.isEmpty() && trimmed.length() < 100) {
                            doc.getKeywords().add(trimmed);
                        }
                    }
                    if (!doc.getKeywords().isEmpty()) {
                        doc.setConfidence("keywords", 0.90);
                    }
                    return;
                }
            }
        }
    }

    // =========================================================================
    // SECTIONS: Find heading elements and collect body text between them
    // =========================================================================

    private static void extractSections(List<Element> elements, PaperDocument doc) {
        List<Integer> headingIndices = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (e.heading || (e.bold && e.text.length() < 100 && SECTION_NUM.matcher(e.text).find())) {
                headingIndices.add(i);
            }
        }

        for (int h = 0; h < headingIndices.size(); h++) {
            int idx = headingIndices.get(h);
            Element heading = elements.get(idx);

            if (doc.getTitle() != null && heading.text.equals(doc.getTitle())) continue;

            String sectionType = SectionClassifier.matchSectionType(heading.text);
            if ("references".equals(sectionType)) continue;

            int nextIdx = h + 1 < headingIndices.size() ? headingIndices.get(h + 1) : elements.size();
            StringBuilder content = new StringBuilder();
            int pageStart = heading.page;
            int pageEnd = pageStart;

            for (int j = idx + 1; j < nextIdx; j++) {
                Element body = elements.get(j);
                if (!body.text.isEmpty()) {
                    if (content.length() > 0) content.append("\n\n");
                    content.append(body.text);
                    pageEnd = body.page;
                }
            }

            doc.getSections().add(new PaperSection(
                sectionType, heading.text, content.toString(), pageStart, pageEnd));
        }
    }

    // =========================================================================
    // REFERENCES: Find "References" heading, parse entries after it
    // =========================================================================

    private static void extractReferences(List<Element> elements, JsonNode rules, PaperDocument doc) {
        List<String> headingPatterns = getStringList(rules, "heading_patterns");
        if (headingPatterns.isEmpty()) {
            headingPatterns = List.of("References", "REFERENCES", "참고문헌", "참고 문헌", "Bibliography");
        }

        String entryPattern = "bracket_number";
        if (rules != null && rules.has("entry_pattern")) {
            entryPattern = rules.get("entry_pattern").asText();
        }

        // Find reference heading
        int refStartIdx = -1;
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            for (String hp : headingPatterns) {
                if (e.text.equalsIgnoreCase(hp) || e.text.equalsIgnoreCase(hp.trim())) {
                    refStartIdx = i + 1;
                    break;
                }
            }
            if (refStartIdx >= 0) break;
        }
        if (refStartIdx < 0) return;

        // Collect all elements after heading and their individual texts
        StringBuilder allRefText = new StringBuilder();
        List<String> elementTexts = new ArrayList<>();
        for (int i = refStartIdx; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (allRefText.length() > 0) allRefText.append("\n");
            allRefText.append(e.text);
            elementTexts.add(e.text);
        }
        if (allRefText.length() == 0) return;

        // Split by entry pattern
        String refStr = allRefText.toString();
        String[] entries;
        if ("dot_number".equals(entryPattern)) {
            entries = Pattern.compile("(?m)(?=^\\s*\\d+\\.\\s)").split(refStr);
        } else if ("paren_number".equals(entryPattern)) {
            entries = Pattern.compile("(?=\\(\\d+\\)\\s)").split(refStr);
        } else if ("apa".equals(entryPattern)) {
            entries = splitByHeuristic(refStr);
        } else if ("auto".equals(entryPattern)) {
            entries = autoSplitReferences(refStr);
        } else {
            entries = Pattern.compile("(?=\\[\\d+\\])").split(refStr);
        }

        // Fallback: if text-based split yields too few results, try element-based split.
        // In hybrid/docling mode, each element may already represent an individual reference
        // because docling segments text at paragraph boundaries.
        if (entries.length <= 2 && elementTexts.size() > 3) {
            String[] elementEntries = splitByElements(elementTexts);
            if (elementEntries.length > entries.length) {
                entries = elementEntries;
            }
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

    /**
     * Element-based reference splitting for hybrid/docling mode.
     * Each element from the IObject tree may already represent an individual reference.
     * Merges consecutive short elements that likely belong to the same entry.
     */
    private static String[] splitByElements(List<String> elementTexts) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String text : elementTexts) {
            text = text.trim();
            if (text.isEmpty()) continue;

            if (current.length() == 0) {
                current.append(text);
                continue;
            }

            // Check if this element starts a new reference
            boolean isNewEntry = false;

            // Signal: element contains a year pattern (likely a complete or starting reference)
            boolean hasYear = YEAR_PATTERN.matcher(text).find();
            // Signal: starts with author-like pattern
            boolean startsWithAuthor = HEURISTIC_LATIN_AUTHOR_START.matcher(text).find()
                || HEURISTIC_KOREAN_AUTHOR_START.matcher(text).find();
            // Signal: starts with number
            boolean startsWithNumber = HEURISTIC_NUMBER_START.matcher(text).find();
            // Signal: previous entry ends with period (complete)
            boolean prevComplete = current.toString().trim().endsWith(".");

            if (startsWithNumber) {
                isNewEntry = true;
            } else if (prevComplete && (startsWithAuthor || hasYear)) {
                isNewEntry = true;
            } else if (prevComplete && text.length() > 40) {
                // Long element after a complete entry is likely a new reference
                isNewEntry = true;
            }

            if (isNewEntry) {
                entries.add(current.toString());
                current = new StringBuilder(text);
            } else {
                current.append(" ").append(text);
            }
        }
        if (current.length() > 0) {
            entries.add(current.toString());
        }

        return entries.toArray(new String[0]);
    }

    /**
     * Auto-detect the reference entry pattern and split accordingly.
     * Tries numbered patterns first; falls back to multi-signal heuristic.
     */
    private static String[] autoSplitReferences(String refStr) {
        // Try [1], [2] pattern
        String[] result = Pattern.compile("(?=\\[\\d+\\])").split(refStr);
        if (result.length > 2) return result;

        // Try 1. 2. pattern
        result = Pattern.compile("(?m)(?=^\\s*\\d+\\.\\s)").split(refStr);
        if (result.length > 2) return result;

        // Try (1) (2) pattern
        result = Pattern.compile("(?=\\(\\d+\\)\\s)").split(refStr);
        if (result.length > 2) return result;

        // Fallback: multi-signal heuristic (handles APA and non-numbered styles)
        return splitByHeuristic(refStr);
    }

    // Patterns for heuristic boundary scoring
    private static final Pattern HEURISTIC_DOI_END = Pattern.compile("10\\.\\d{4,9}/\\S+$");
    private static final Pattern HEURISTIC_YEAR_END = Pattern.compile("\\((?:19|20)\\d{2}[a-z]?\\)\\.?\\s*$");
    private static final Pattern HEURISTIC_LATIN_AUTHOR_START = Pattern.compile("^[A-Z][a-z]+,\\s");
    private static final Pattern HEURISTIC_KOREAN_AUTHOR_START = Pattern.compile("^[\\p{IsHangul}]{2,}[,(\\s]");
    private static final Pattern HEURISTIC_NUMBER_START = Pattern.compile(
        "^\\s*(?:\\[\\d+\\]|\\d+[.)]+\\s|\\(\\d+\\)\\s)");

    /**
     * Split reference text using multiple signals at each line boundary.
     * Scores each boundary and splits where score >= threshold.
     */
    private static String[] splitByHeuristic(String refStr) {
        String[] lines = refStr.split("\n");
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (current.length() == 0) {
                current.append(line);
                continue;
            }

            int score = scoreBoundary(lines, i);

            if (score >= 3) {
                entries.add(current.toString());
                current = new StringBuilder(line);
            } else {
                current.append(" ").append(line);
            }
        }
        if (current.length() > 0) {
            entries.add(current.toString());
        }

        return entries.toArray(new String[0]);
    }

    /**
     * Score a line boundary to determine if a new reference entry starts here.
     * Positive score = likely new entry; negative = continuation of previous.
     */
    private static int scoreBoundary(String[] lines, int currentIdx) {
        String prevLine = lines[currentIdx - 1].trim();
        String currLine = lines[currentIdx].trim();
        if (currLine.isEmpty()) return 0;
        int score = 0;

        // S1: Previous line ends with completion signal (period after page/year/DOI)
        if (prevLine.endsWith(".") || prevLine.endsWith(".)")) score += 3;
        if (HEURISTIC_DOI_END.matcher(prevLine).find()) score += 3;

        // S2: Current line starts with author name pattern
        if (HEURISTIC_LATIN_AUTHOR_START.matcher(currLine).find()) score += 3;
        if (HEURISTIC_KOREAN_AUTHOR_START.matcher(currLine).find()) score += 3;

        // S3: Current line starts with number pattern (definitive)
        if (HEURISTIC_NUMBER_START.matcher(currLine).find()) score += 5;

        // S4: Previous line is incomplete (no period, ends with lowercase/comma/hyphen)
        if (!prevLine.endsWith(".") && !prevLine.endsWith(")")
            && prevLine.matches(".*[a-z,\\-]$")) score -= 3;

        // S5: Previous line contains year pattern near end (year often closes an entry)
        if (HEURISTIC_YEAR_END.matcher(prevLine).find()) score += 2;

        return score;
    }

    private static void parseReferenceFields(String text, PaperReference ref) {
        int fieldsParsed = 0;
        Matcher doiMatcher = DOI_PATTERN.matcher(text);
        if (doiMatcher.find()) { ref.setDoi(doiMatcher.group()); fieldsParsed++; }
        Matcher yearMatcher = YEAR_PATTERN.matcher(text);
        if (yearMatcher.find()) { ref.setYear(Integer.parseInt(yearMatcher.group(1))); fieldsParsed++; }
        Matcher titleMatcher = QUOTED_TITLE.matcher(text);
        if (titleMatcher.find()) {
            ref.setTitle(titleMatcher.group(1).trim());
            String before = text.substring(0, titleMatcher.start()).trim();
            if (before.endsWith(",")) before = before.substring(0, before.length() - 1);
            if (!before.isEmpty()) { ref.setAuthors(parseRefAuthors(before)); fieldsParsed += 2; }
            else fieldsParsed++;
        }
        ref.setConfidence(Math.min(1.0, fieldsParsed * 0.2));
    }

    private static List<String> parseRefAuthors(String text) {
        text = text.replaceAll("[,;.]+$", "").trim();
        String[] parts = text.split("\\s*[,;]\\s*(?:and\\s+|&\\s+)?|\\s+and\\s+|\\s*&\\s*");
        List<String> authors = new ArrayList<>();
        for (String p : parts) {
            p = p.replaceAll("[¹²³⁴⁵⁶⁷⁸⁹⁰*†‡]", "").trim();
            if (!p.isEmpty() && p.length() > 1) authors.add(p);
        }
        return authors;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Set<String> getStringSet(JsonNode rules, String field) {
        Set<String> result = new HashSet<>();
        if (rules != null && rules.has(field) && rules.get(field).isArray()) {
            for (JsonNode item : rules.get(field)) result.add(item.asText().toLowerCase());
        }
        return result;
    }

    private static List<String> getStringList(JsonNode rules, String field) {
        List<String> result = new ArrayList<>();
        if (rules != null && rules.has(field) && rules.get(field).isArray()) {
            for (JsonNode item : rules.get(field)) result.add(item.asText());
        }
        return result;
    }

    private static List<Pattern> getPatternList(JsonNode rules, String field) {
        List<Pattern> result = new ArrayList<>();
        if (rules != null && rules.has(field) && rules.get(field).isArray()) {
            for (JsonNode item : rules.get(field)) {
                result.add(Pattern.compile(Pattern.quote(item.asText()), Pattern.CASE_INSENSITIVE));
            }
        }
        return result;
    }
}
