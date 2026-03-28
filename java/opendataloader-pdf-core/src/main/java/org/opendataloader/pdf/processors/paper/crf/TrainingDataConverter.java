package org.opendataloader.pdf.processors.paper.crf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TrainingDataConverter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<LabeledZoneData> fromReviewJson(Path reviewJsonPath) throws IOException {
        JsonNode root = MAPPER.readTree(reviewJsonPath.toFile());
        List<LabeledZoneData> result = new ArrayList<>();
        JsonNode zones = root.get("zones");
        if (zones == null || !zones.isArray()) return result;
        for (JsonNode zone : zones) {
            int index = zone.get("index").asInt();
            int page = zone.get("page").asInt();
            String textPreview = zone.has("text_preview") ? zone.get("text_preview").asText() : "";
            String classifiedAs = zone.get("classified_as").asText();
            String correctedAs = zone.has("corrected_as") && !zone.get("corrected_as").isNull()
                ? zone.get("corrected_as").asText() : null;
            String label = correctedAs != null ? correctedAs : classifiedAs;
            result.add(new LabeledZoneData(index, page, textPreview, label));
        }
        return result;
    }

    // Use a regular class instead of record (Java 11 compatibility)
    public static final class LabeledZoneData {
        private final int index;
        private final int page;
        private final String textPreview;
        private final String label;

        public LabeledZoneData(int index, int page, String textPreview, String label) {
            this.index = index;
            this.page = page;
            this.textPreview = textPreview;
            this.label = label;
        }

        public int index() { return index; }
        public int page() { return page; }
        public String textPreview() { return textPreview; }
        public String label() { return label; }
    }
}
