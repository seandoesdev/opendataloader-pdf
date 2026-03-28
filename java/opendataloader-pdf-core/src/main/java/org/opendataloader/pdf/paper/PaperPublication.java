package org.opendataloader.pdf.paper;

public class PaperPublication {
    private String venue;
    private String date;
    private String volume;
    private String issue;
    private String pages;

    public PaperPublication() {}

    public String getVenue() { return venue; }
    public String getDate() { return date; }
    public String getVolume() { return volume; }
    public String getIssue() { return issue; }
    public String getPages() { return pages; }

    public void setVenue(String venue) { this.venue = venue; }
    public void setDate(String date) { this.date = date; }
    public void setVolume(String volume) { this.volume = volume; }
    public void setIssue(String issue) { this.issue = issue; }
    public void setPages(String pages) { this.pages = pages; }
}
