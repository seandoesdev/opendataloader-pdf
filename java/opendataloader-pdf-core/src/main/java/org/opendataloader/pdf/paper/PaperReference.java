package org.opendataloader.pdf.paper;

import java.util.ArrayList;
import java.util.List;

public class PaperReference {
    private int id;
    private String raw;
    private List<String> authors;
    private String title;
    private String venue;
    private Integer year;
    private String doi;
    private double confidence;
    private List<CitationLink> citationsInText;

    public PaperReference(int id, String raw) {
        this.id = id;
        this.raw = raw;
        this.authors = new ArrayList<>();
        this.citationsInText = new ArrayList<>();
        this.confidence = 0.0;
    }

    public int getId() { return id; }
    public String getRaw() { return raw; }
    public List<String> getAuthors() { return authors; }
    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public Integer getYear() { return year; }
    public String getDoi() { return doi; }
    public double getConfidence() { return confidence; }
    public List<CitationLink> getCitationsInText() { return citationsInText; }

    public void setAuthors(List<String> authors) { this.authors = authors; }
    public void setTitle(String title) { this.title = title; }
    public void setVenue(String venue) { this.venue = venue; }
    public void setYear(Integer year) { this.year = year; }
    public void setDoi(String doi) { this.doi = doi; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public void addCitation(CitationLink citation) {
        this.citationsInText.add(citation);
    }
}
