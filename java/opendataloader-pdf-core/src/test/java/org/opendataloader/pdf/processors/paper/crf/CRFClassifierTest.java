package org.opendataloader.pdf.processors.paper.crf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;
import org.opendataloader.pdf.paper.ZoneType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CRFClassifierTest {

    @Test
    void testClassifyWithoutModelReturnsUnknown() {
        CRFClassifier classifier = new CRFClassifier();
        List<Zone> zones = List.of(
                createZone(1, "Some title text", 18.0, true),
                createZone(1, "Body paragraph content here.", 10.0, false)
        );

        List<CRFClassifier.Classification> results = classifier.classify(zones);

        assertThat(results).hasSize(2);
        for (CRFClassifier.Classification c : results) {
            assertThat(c.type()).isEqualTo(ZoneType.UNKNOWN);
            assertThat(c.confidence()).isEqualTo(0.0);
        }
    }

    @Test
    void testModelNotLoadedReturnsFalse() {
        CRFClassifier classifier = new CRFClassifier();
        assertThat(classifier.isModelLoaded()).isFalse();
    }

    @Test
    void testSaveAndLoadModel(@TempDir Path tempDir) throws Exception {
        CRFClassifier classifier = new CRFClassifier();

        List<List<CRFClassifier.LabeledZone>> trainingData = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<CRFClassifier.LabeledZone> document = List.of(
                    new CRFClassifier.LabeledZone(
                            createZone(1, "A Study of Machine Learning Approaches", 18.0, true),
                            ZoneType.TITLE),
                    new CRFClassifier.LabeledZone(
                            createZone(1, "John Doe, Jane Smith, University of Testing", 10.0, false),
                            ZoneType.AUTHOR_BLOCK),
                    new CRFClassifier.LabeledZone(
                            createZone(1, "Abstract This paper presents a novel approach to classification.", 10.0, false),
                            ZoneType.ABSTRACT),
                    new CRFClassifier.LabeledZone(
                            createZone(1, "In this section we describe the methodology used in our experiments.", 10.0, false),
                            ZoneType.BODY_TEXT)
            );
            trainingData.add(document);
        }

        classifier.train(trainingData);
        assertThat(classifier.isModelLoaded()).isTrue();

        Path modelPath = tempDir.resolve("test-model.ser");
        classifier.save(modelPath);
        assertThat(modelPath.toFile()).exists();

        CRFClassifier loaded = CRFClassifier.load(modelPath);
        assertThat(loaded.isModelLoaded()).isTrue();

        List<Zone> testZones = List.of(
                createZone(1, "A Study of Machine Learning Approaches", 18.0, true),
                createZone(1, "John Doe, Jane Smith, University of Testing", 10.0, false),
                createZone(1, "Abstract This paper presents a novel approach to classification.", 10.0, false),
                createZone(1, "In this section we describe the methodology used in our experiments.", 10.0, false)
        );

        List<CRFClassifier.Classification> results = loaded.classify(testZones);
        assertThat(results).hasSize(4);
        assertThat(results).anyMatch(c -> c.type() != ZoneType.UNKNOWN);
    }

    private Zone createZone(int page, String text, double fontSize, boolean bold) {
        ZoneFeatures features = new ZoneFeatures(0.1, fontSize, fontSize,
                fontSize / 10.0, bold, true, false, 0.5, 2, 30,
                false, false, false, false, page - 1, 0.1, false);
        Zone zone = new Zone(page, new BoundingBox(0, 72, 100, 540, 700),
                new ArrayList<>(), features);
        zone.setConfidence(0.8);
        zone.setTextOverride(text);
        return zone;
    }
}
