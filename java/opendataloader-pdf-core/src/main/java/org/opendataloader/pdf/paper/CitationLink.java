package org.opendataloader.pdf.paper;

public class CitationLink {
    private final int page;
    private final String context;

    public CitationLink(int page, String context) {
        this.page = page;
        this.context = context;
    }

    public int getPage() { return page; }
    public String getContext() { return context; }
}
