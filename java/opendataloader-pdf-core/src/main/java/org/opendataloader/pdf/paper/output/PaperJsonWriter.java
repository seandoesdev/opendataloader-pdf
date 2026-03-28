package org.opendataloader.pdf.paper.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;
import org.opendataloader.pdf.paper.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class PaperJsonWriter {
    private static final Logger LOGGER = Logger.getLogger(PaperJsonWriter.class.getName());

    public static void write(PaperDocument doc, String inputPdfName, String outputFolder) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode root = mapper.createObjectNode();

        // title
        if (doc.getTitle() != null) root.put("title", doc.getTitle());
        if (doc.getTitleEn() != null) root.put("title_en", doc.getTitleEn());

        // authors
        ArrayNode authorsNode = root.putArray("authors");
        for (PaperAuthor author : doc.getAuthors()) {
            ObjectNode an = mapper.createObjectNode();
            an.put("name", author.getName());
            if (author.getAffiliation() != null) an.put("affiliation", author.getAffiliation());
            if (author.getEmail() != null) an.put("email", author.getEmail());
            an.put("is_corresponding", author.isCorresponding());
            authorsNode.add(an);
        }

        // abstract
        if (doc.getAbstractText() != null) root.put("abstract", doc.getAbstractText());

        // keywords
        ArrayNode kwNode = root.putArray("keywords");
        for (String kw : doc.getKeywords()) kwNode.add(kw);

        // doi
        if (doc.getDoi() != null) root.put("doi", doc.getDoi());

        // publication
        if (doc.getPublication() != null) {
            ObjectNode pubNode = root.putObject("publication");
            PaperPublication pub = doc.getPublication();
            if (pub.getVenue() != null) pubNode.put("venue", pub.getVenue());
            if (pub.getDate() != null) pubNode.put("date", pub.getDate());
            if (pub.getVolume() != null) pubNode.put("volume", pub.getVolume());
            if (pub.getIssue() != null) pubNode.put("issue", pub.getIssue());
            if (pub.getPages() != null) pubNode.put("pages", pub.getPages());
        }

        // sections
        ArrayNode sectionsNode = root.putArray("sections");
        for (PaperSection section : doc.getSections()) {
            ObjectNode sn = mapper.createObjectNode();
            sn.put("type", section.getType());
            sn.put("title", section.getTitle());
            sn.put("content", section.getContent());
            sn.put("page_start", section.getPageStart());
            sn.put("page_end", section.getPageEnd());
            sectionsNode.add(sn);
        }

        // references
        ArrayNode refsNode = root.putArray("references");
        for (PaperReference ref : doc.getReferences()) {
            ObjectNode rn = mapper.createObjectNode();
            rn.put("id", ref.getId());
            rn.put("raw", ref.getRaw());
            ArrayNode refAuthors = rn.putArray("authors");
            for (String a : ref.getAuthors()) refAuthors.add(a);
            if (ref.getTitle() != null) rn.put("title", ref.getTitle());
            if (ref.getVenue() != null) rn.put("venue", ref.getVenue());
            if (ref.getYear() != null) rn.put("year", ref.getYear());
            if (ref.getDoi() != null) rn.put("doi", ref.getDoi());
            ArrayNode citNode = rn.putArray("citations_in_text");
            for (CitationLink cl : ref.getCitationsInText()) {
                ObjectNode cn = mapper.createObjectNode();
                cn.put("page", cl.getPage());
                cn.put("context", cl.getContext());
                citNode.add(cn);
            }
            refsNode.add(rn);
        }

        // unlinked_citations
        if (!doc.getUnlinkedCitations().isEmpty()) {
            ArrayNode unlNode = root.putArray("unlinked_citations");
            for (CitationLink cl : doc.getUnlinkedCitations()) {
                ObjectNode cn = mapper.createObjectNode();
                cn.put("page", cl.getPage());
                cn.put("context", cl.getContext());
                unlNode.add(cn);
            }
        }

        // metadata
        ObjectNode metaNode = root.putObject("metadata");
        metaNode.put("source_file", doc.getSourceFile());
        metaNode.put("total_pages", doc.getTotalPages());
        if (doc.getLanguage() != null) metaNode.put("language", doc.getLanguage());
        metaNode.put("extraction_mode", doc.getExtractionMode());
        ObjectNode confNode = metaNode.putObject("confidence");
        for (var entry : doc.getConfidence().entrySet()) {
            confNode.put(entry.getKey(), Math.round(entry.getValue() * 100.0) / 100.0);
        }

        // Write file
        String baseName = new File(inputPdfName).getName().replaceFirst("\\.pdf$", "");
        File outputFile = Paths.get(outputFolder, baseName + ".paper.json").toFile();
        mapper.writeValue(outputFile, root);
        LOGGER.info("Paper JSON written to: " + outputFile.getAbsolutePath());
    }
}
