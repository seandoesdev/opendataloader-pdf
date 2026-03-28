package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.paper.*;
import org.opendataloader.pdf.paper.output.PaperJsonWriter;
import org.opendataloader.pdf.paper.output.PaperMarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PaperProcessor {
    private static final Logger LOGGER = Logger.getLogger(PaperProcessor.class.getName());

    public static void process(String inputPdfName, List<List<IObject>> contents,
                                Config config) throws IOException {
        LOGGER.info("Paper mode: processing " + inputPdfName);

        int totalPages = contents.size();
        double avgBodyFontSize = computeAvgBodyFontSize(contents);

        // Load classifier
        ZoneClassifier classifier;
        if (config.getPaperWeightsPath() != null) {
            classifier = ZoneClassifier.fromFile(config.getPaperWeightsPath());
        } else {
            classifier = ZoneClassifier.withDefaultWeights();
        }

        // Step 1: Detect zones from all pages
        List<Zone> allZones = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            List<Zone> pageZones = ZoneDetector.detect(contents.get(i), i, totalPages, avgBodyFontSize);
            allZones.addAll(pageZones);
        }
        LOGGER.info("Paper mode: detected " + allZones.size() + " zones across " + totalPages + " pages");

        // Step 2: Classify zones
        classifier.classify(allZones);

        // Step 3: Extract structured data
        PaperDocument doc = new PaperDocument(
            new java.io.File(inputPdfName).getName(), totalPages);

        TitleExtractor.extract(allZones, doc);
        AuthorExtractor.extract(allZones, doc);
        AbstractExtractor.extract(allZones, doc);
        MetadataExtractor.extract(allZones, doc);
        SectionClassifier.classify(allZones, doc);
        ReferenceParser.parse(allZones, doc);
        CitationLinker.link(allZones, doc);

        // Step 4: Generate outputs
        String outputFolder = config.getOutputFolder();
        if (outputFolder == null) {
            outputFolder = new java.io.File(inputPdfName).getParent();
            if (outputFolder == null) outputFolder = ".";
        }
        PaperJsonWriter.write(doc, inputPdfName, outputFolder);
        PaperMarkdownGenerator.write(doc, inputPdfName, outputFolder);

        LOGGER.info("Paper mode: completed " + inputPdfName +
            " (title=" + (doc.getTitle() != null ? "yes" : "no") +
            ", authors=" + doc.getAuthors().size() +
            ", refs=" + doc.getReferences().size() + ")");
    }

    private static double computeAvgBodyFontSize(List<List<IObject>> contents) {
        double sum = 0;
        int count = 0;
        for (List<IObject> page : contents) {
            for (IObject obj : page) {
                if (obj instanceof SemanticTextNode) {
                    double size = ((SemanticTextNode) obj).getFontSize();
                    if (size > 0) { sum += size; count++; }
                }
            }
        }
        return count > 0 ? sum / count : 12.0;
    }
}
