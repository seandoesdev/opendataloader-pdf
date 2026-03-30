package org.opendataloader.pdf.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaperDocument {
    private String title;
    private String titleEn;
    private String abstractText;
    private String doi;
    private String language;
    private String extractionMode;
    private String sourceFile;
    private List<PaperAuthor> authors;
    private List<String> keywords;
    private List<PaperSection> sections;
    private List<PaperReference> references;
    private List<CitationLink> unlinkedCitations;
    private PaperPublication publication;
    private int totalPages;
    private Map<String, Double> confidence;

    /** Internal index of the title element — not serialized to JSON output. */
    private transient int titleElementIndex = -1;

    public PaperDocument(String sourceFile, int totalPages) {
        this.sourceFile = sourceFile;
        this.totalPages = totalPages;
        this.extractionMode = "rule-based";
        this.authors = new ArrayList<>();
        this.keywords = new ArrayList<>();
        this.sections = new ArrayList<>();
        this.references = new ArrayList<>();
        this.unlinkedCitations = new ArrayList<>();
        this.confidence = new HashMap<>();
    }

    public String getTitle() { return title; }
    public String getTitleEn() { return titleEn; }
    public String getAbstractText() { return abstractText; }
    public String getDoi() { return doi; }
    public String getLanguage() { return language; }
    public String getExtractionMode() { return extractionMode; }
    public String getSourceFile() { return sourceFile; }
    public List<PaperAuthor> getAuthors() { return authors; }
    public List<String> getKeywords() { return keywords; }
    public List<PaperSection> getSections() { return sections; }
    public List<PaperReference> getReferences() { return references; }
    public List<CitationLink> getUnlinkedCitations() { return unlinkedCitations; }
    public PaperPublication getPublication() { return publication; }
    public int getTotalPages() { return totalPages; }
    public Map<String, Double> getConfidence() { return confidence; }

    public void setTitle(String title) { this.title = title; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public void setDoi(String doi) { this.doi = doi; }
    public void setPublication(PaperPublication publication) { this.publication = publication; }
    public void setLanguage(String language) { this.language = language; }
    public void setExtractionMode(String extractionMode) { this.extractionMode = extractionMode; }

    public int getTitleElementIndex() { return titleElementIndex; }
    public void setTitleElementIndex(int titleElementIndex) { this.titleElementIndex = titleElementIndex; }

    public void setConfidence(String field, double value) {
        this.confidence.put(field, value);
    }
}
