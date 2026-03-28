package org.opendataloader.pdf.paper;

public class PaperAuthor {
    private String name;
    private String affiliation;
    private String email;
    private boolean corresponding;

    public PaperAuthor(String name, String affiliation, String email, boolean corresponding) {
        this.name = name;
        this.affiliation = affiliation;
        this.email = email;
        this.corresponding = corresponding;
    }

    public String getName() { return name; }
    public String getAffiliation() { return affiliation; }
    public String getEmail() { return email; }
    public boolean isCorresponding() { return corresponding; }
    public void setAffiliation(String affiliation) { this.affiliation = affiliation; }
    public void setEmail(String email) { this.email = email; }
}
