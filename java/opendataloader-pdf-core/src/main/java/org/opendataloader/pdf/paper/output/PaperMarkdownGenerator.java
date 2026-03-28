package org.opendataloader.pdf.paper.output;

import org.opendataloader.pdf.paper.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class PaperMarkdownGenerator {
    private static final Logger LOGGER = Logger.getLogger(PaperMarkdownGenerator.class.getName());

    public static void write(PaperDocument doc, String inputPdfName, String outputFolder) throws IOException {
        String baseName = new File(inputPdfName).getName().replaceFirst("\\.pdf$", "");
        File outputFile = Paths.get(outputFolder, baseName + ".paper.md").toFile();

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

            // YAML frontmatter
            w.write("---\n");
            if (doc.getTitle() != null) w.write("title: \"" + escapeYaml(doc.getTitle()) + "\"\n");
            if (doc.getTitleEn() != null) w.write("title_en: \"" + escapeYaml(doc.getTitleEn()) + "\"\n");

            if (!doc.getAuthors().isEmpty()) {
                w.write("authors:\n");
                for (PaperAuthor a : doc.getAuthors()) {
                    w.write("  - name: " + a.getName() + "\n");
                    if (a.getAffiliation() != null) w.write("    affiliation: " + a.getAffiliation() + "\n");
                    if (a.getEmail() != null) w.write("    email: " + a.getEmail() + "\n");
                    if (a.isCorresponding()) w.write("    corresponding: true\n");
                }
            }

            if (doc.getDoi() != null) w.write("doi: \"" + doc.getDoi() + "\"\n");
            if (doc.getPublication() != null) {
                PaperPublication pub = doc.getPublication();
                if (pub.getVenue() != null) w.write("venue: \"" + escapeYaml(pub.getVenue()) + "\"\n");
                if (pub.getDate() != null) w.write("date: " + pub.getDate() + "\n");
                if (pub.getVolume() != null) w.write("volume: \"" + pub.getVolume() + "\"\n");
                if (pub.getIssue() != null) w.write("issue: \"" + pub.getIssue() + "\"\n");
                if (pub.getPages() != null) w.write("pages: \"" + pub.getPages() + "\"\n");
            }

            if (!doc.getKeywords().isEmpty()) {
                w.write("keywords: [" + String.join(", ", doc.getKeywords()) + "]\n");
            }
            if (doc.getLanguage() != null) w.write("language: " + doc.getLanguage() + "\n");
            w.write("---\n\n");

            // Abstract
            if (doc.getAbstractText() != null) {
                w.write("## Abstract\n\n");
                w.write(doc.getAbstractText() + "\n\n");
            }

            // Sections
            for (PaperSection section : doc.getSections()) {
                w.write("## " + section.getTitle() + "\n\n");
                if (section.getContent() != null && !section.getContent().isEmpty()) {
                    w.write(section.getContent() + "\n\n");
                }
            }

            // References
            if (!doc.getReferences().isEmpty()) {
                w.write("## References\n\n");
                for (PaperReference ref : doc.getReferences()) {
                    w.write("[^" + ref.getId() + "]: ");
                    // Build formatted reference
                    StringBuilder refLine = new StringBuilder();
                    if (!ref.getAuthors().isEmpty()) {
                        refLine.append(String.join(", ", ref.getAuthors())).append(". ");
                    }
                    if (ref.getTitle() != null) {
                        refLine.append("\"").append(ref.getTitle()).append(".\" ");
                    }
                    if (ref.getVenue() != null) {
                        refLine.append(ref.getVenue()).append(", ");
                    }
                    if (ref.getYear() != null) {
                        refLine.append(ref.getYear()).append(".");
                    }
                    if (ref.getDoi() != null) {
                        refLine.append(" doi:").append(ref.getDoi());
                    }
                    String formatted = refLine.toString().trim();
                    w.write(formatted.isEmpty() ? ref.getRaw() : formatted);
                    w.write("\n");
                }
            }
        }

        LOGGER.info("Paper Markdown written to: " + outputFile.getAbsolutePath());
    }

    private static String escapeYaml(String text) {
        return text.replace("\"", "\\\"");
    }
}
