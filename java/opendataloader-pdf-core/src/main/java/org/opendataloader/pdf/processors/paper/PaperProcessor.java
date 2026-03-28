package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
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

        // Detect zones (shared by all layers)
        List<Zone> allZones = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            allZones.addAll(ZoneDetector.detect(contents.get(i), i, totalPages, avgBodyFontSize));
        }
        LOGGER.info("Paper mode: detected " + allZones.size() + " zones");

        // Classify zones with rule engine (Layer 3 — always runs as baseline)
        ZoneClassifier classifier = config.getPaperWeightsPath() != null
            ? ZoneClassifier.fromFile(config.getPaperWeightsPath())
            : ZoneClassifier.withDefaultWeights();
        classifier.classify(allZones);

        // === Layer 3: Rule-based extraction (always runs) ===
        PaperDocument ruleResult = extractWithRules(allZones, inputPdfName, totalPages);

        // === Layer 1: Template-based extraction ===
        PaperDocument templateResult = null;
        TemplateRegistry registry = config.getPaperTemplateDir() != null
            ? TemplateRegistry.loadFromDir(config.getPaperTemplateDir())
            : TemplateRegistry.loadDefault();

        String firstPageText = getFirstPageText(allZones);
        String journalId = JournalFingerprinter.identify(firstPageText, registry);
        LOGGER.info("Paper mode: journal identified as '" + journalId + "'");

        JsonNode template = registry.findTemplate(JournalFingerprinter.extractDoi(firstPageText));
        if (template == null) {
            template = registry.findTemplateByText(firstPageText);
        }
        if (template != null) {
            templateResult = new PaperDocument(
                new java.io.File(inputPdfName).getName(), totalPages);
            TemplateBasedExtractor.extract(allZones, template, templateResult);
            templateResult.setExtractionMode("template:" + journalId);
            LOGGER.info("Paper mode: template extraction completed (journal=" + journalId + ")");
        }

        // === Layer 2: CRF model ===
        PaperDocument crfResult = null;
        if (config.getPaperCrfModelPath() != null) {
            java.nio.file.Path crfModelPath = java.nio.file.Path.of(config.getPaperCrfModelPath());
            if (java.nio.file.Files.exists(crfModelPath)) {
                try {
                    org.opendataloader.pdf.processors.paper.crf.CRFClassifier crfClassifier =
                        org.opendataloader.pdf.processors.paper.crf.CRFClassifier.load(crfModelPath);
                    if (crfClassifier.isModelLoaded()) {
                        List<org.opendataloader.pdf.processors.paper.crf.CRFClassifier.Classification> crfResults =
                            crfClassifier.classify(allZones);

                        // Create a copy of zones with CRF classifications applied
                        List<Zone> crfZones = new ArrayList<>();
                        double totalCrfConf = 0;
                        for (int i = 0; i < allZones.size() && i < crfResults.size(); i++) {
                            Zone original = allZones.get(i);
                            Zone copy = new Zone(original.getPageNumber(), original.getBounds(),
                                original.getContents(), original.getFeatures());
                            copy.setTextOverride(original.getTextContent());

                            org.opendataloader.pdf.processors.paper.crf.CRFClassifier.Classification c = crfResults.get(i);
                            if (c.confidence() >= 0.8) {
                                copy.setType(c.type());
                                copy.setConfidence(c.confidence());
                            } else {
                                copy.setType(original.getType());
                                copy.setConfidence(original.getConfidence());
                            }
                            crfZones.add(copy);
                            totalCrfConf += c.confidence();
                        }
                        double avgCrfConf = crfResults.isEmpty() ? 0 : totalCrfConf / crfResults.size();

                        if (avgCrfConf >= 0.7) {
                            crfResult = extractWithRules(crfZones, inputPdfName, totalPages);
                            crfResult.setExtractionMode("crf");
                            LOGGER.info("Paper mode: CRF extraction completed (avg confidence: " +
                                String.format("%.2f", avgCrfConf) + ")");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("CRF classification failed, falling back: " + e.getMessage());
                }
            }
        }

        // === Merge results ===
        PaperDocument finalDoc = ResultMerger.merge(templateResult, crfResult, ruleResult);
        finalDoc.setExtractionMode(templateResult != null
            ? "template:" + journalId + "+rules" : "rules");

        // Diagnostic logging
        PaperZoneLogger.log(allZones, finalDoc);

        // Generate outputs
        String outputFolder = config.getOutputFolder();
        if (outputFolder == null) {
            outputFolder = new java.io.File(inputPdfName).getParent();
            if (outputFolder == null) outputFolder = ".";
        }
        PaperJsonWriter.write(finalDoc, inputPdfName, outputFolder);
        PaperMarkdownGenerator.write(finalDoc, inputPdfName, outputFolder);

        // Write to review queue if low confidence
        ReviewQueueWriter.write(finalDoc, allZones, inputPdfName, config.getPaperReviewDir());

        LOGGER.info("Paper mode: completed " + inputPdfName +
            " (title=" + (finalDoc.getTitle() != null ? "yes" : "no") +
            ", authors=" + finalDoc.getAuthors().size() +
            ", refs=" + finalDoc.getReferences().size() +
            ", mode=" + finalDoc.getExtractionMode() + ")");
    }

    private static PaperDocument extractWithRules(List<Zone> zones, String inputPdfName, int totalPages) {
        PaperDocument doc = new PaperDocument(
            new java.io.File(inputPdfName).getName(), totalPages);
        TitleExtractor.extract(zones, doc);
        AuthorExtractor.extract(zones, doc);
        AbstractExtractor.extract(zones, doc);
        MetadataExtractor.extract(zones, doc);
        SectionClassifier.classify(zones, doc);
        ReferenceParser.parse(zones, doc);
        CitationLinker.link(zones, doc);
        doc.setExtractionMode("rules");
        return doc;
    }

    private static String getFirstPageText(List<Zone> zones) {
        StringBuilder sb = new StringBuilder();
        for (Zone zone : zones) {
            if (zone.getPageNumber() <= 1) {
                sb.append(zone.getTextContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private static double computeAvgBodyFontSize(List<List<IObject>> contents) {
        double sum = 0; int count = 0;
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
