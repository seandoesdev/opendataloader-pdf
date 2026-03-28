package org.opendataloader.pdf.paper;

public class PaperSection {
    private String type;
    private String title;
    private String content;
    private int pageStart;
    private int pageEnd;

    public PaperSection(String type, String title, String content, int pageStart, int pageEnd) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getPageStart() { return pageStart; }
    public int getPageEnd() { return pageEnd; }
}
