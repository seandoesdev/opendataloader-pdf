package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;
import org.opendataloader.pdf.paper.*;
import org.opendataloader.pdf.paper.Zone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

public class ReviewQueueWriter {
    private static final Logger LOGGER = Logger.getLogger(ReviewQueueWriter.class.getName());

    public static void write(PaperDocument doc, List<Zone> zones,
                              String inputPdfName, String reviewDir) throws IOException {
        if (reviewDir == null) return;

        double avgConfidence = doc.getConfidence().values().stream()
            .mapToDouble(Double::doubleValue).average().orElse(0);
        if (avgConfidence >= 0.9) return;

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode root = mapper.createObjectNode();
        root.put("source", doc.getSourceFile());
        root.put("overall_confidence", Math.round(avgConfidence * 100.0) / 100.0);

        ObjectNode fields = root.putObject("fields");
        addField(fields, "title", doc.getTitle(), doc.getConfidence().getOrDefault("title", 0.0));
        addField(fields, "abstract", doc.getAbstractText(), doc.getConfidence().getOrDefault("abstract", 0.0));
        addField(fields, "doi", doc.getDoi(), doc.getConfidence().getOrDefault("doi", 0.0));

        if (zones != null) {
            ArrayNode zonesArray = root.putArray("zones");
            for (int i = 0; i < zones.size(); i++) {
                Zone zone = zones.get(i);
                ObjectNode zoneNode = mapper.createObjectNode();
                zoneNode.put("index", i);
                zoneNode.put("page", zone.getPageNumber());
                String preview = zone.getTextContent();
                zoneNode.put("text_preview", preview.length() > 100 ? preview.substring(0, 100) : preview);
                zoneNode.put("classified_as", zone.getType().name());
                zoneNode.putNull("corrected_as");
                zonesArray.add(zoneNode);
            }
        }

        String baseName = new File(inputPdfName).getName().replaceFirst("\\.pdf$", "");
        File outputFile = Paths.get(reviewDir, baseName + ".review.json").toFile();
        outputFile.getParentFile().mkdirs();
        mapper.writeValue(outputFile, root);
        LOGGER.info("Review queue written to: " + outputFile.getAbsolutePath());
    }

    private static void addField(ObjectNode fields, String name, String value, double confidence) {
        ObjectNode field = fields.putObject(name);
        if (value != null) field.put("value", value); else field.putNull("value");
        field.put("confidence", Math.round(confidence * 100.0) / 100.0);
        field.putNull("corrected");
    }
}
