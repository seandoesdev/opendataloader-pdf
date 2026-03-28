package org.opendataloader.pdf.processors.paper.crf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.List;

public class TrainingDataConverterTest {

    @Test
    void testConvertReviewJsonToTrainingData(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("source", "test.pdf");
        ArrayNode zones = root.putArray("zones");

        ObjectNode zone1 = mapper.createObjectNode();
        zone1.put("index", 0); zone1.put("page", 1);
        zone1.put("text_preview", "Original Article");
        zone1.put("classified_as", "TITLE");
        zone1.put("corrected_as", "PAGE_METADATA");
        zones.add(zone1);

        ObjectNode zone2 = mapper.createObjectNode();
        zone2.put("index", 1); zone2.put("page", 1);
        zone2.put("text_preview", "Real Paper Title");
        zone2.put("classified_as", "BODY_TEXT");
        zone2.put("corrected_as", "TITLE");
        zones.add(zone2);

        Path file = tempDir.resolve("test.review.json");
        mapper.writeValue(file.toFile(), root);

        List<TrainingDataConverter.LabeledZoneData> data = TrainingDataConverter.fromReviewJson(file);
        assertEquals(2, data.size());
        assertEquals("PAGE_METADATA", data.get(0).label());
        assertEquals("TITLE", data.get(1).label());
    }

    @Test
    void testUseOriginalLabelWhenNotCorrected(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("source", "test.pdf");
        ArrayNode zones = root.putArray("zones");

        ObjectNode zone1 = mapper.createObjectNode();
        zone1.put("index", 0); zone1.put("page", 1);
        zone1.put("text_preview", "Actual Title");
        zone1.put("classified_as", "TITLE");
        zone1.putNull("corrected_as");
        zones.add(zone1);

        Path file = tempDir.resolve("test2.review.json");
        mapper.writeValue(file.toFile(), root);

        List<TrainingDataConverter.LabeledZoneData> data = TrainingDataConverter.fromReviewJson(file);
        assertEquals(1, data.size());
        assertEquals("TITLE", data.get(0).label());
    }
}
