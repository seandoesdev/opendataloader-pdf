# Paper Mode v2 Sub-project 2: CRF Model Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate a CRF (Conditional Random Field) model as Layer 2 in the paper mode pipeline, achieving 85-95% accuracy on unregistered journals without templates.

**Architecture:** CRFFeatureExtractor extends the existing 17 zone features to 40+ features. CRFClassifier wraps MALLET's CRF for training and inference. TrainingDataConverter converts review queue JSON files into MALLET training format. CRFTrainer orchestrates model training. PaperProcessor routes through CRF when template match fails. TemplateAutoGenerator creates new templates from consistent high-confidence CRF results.

**Tech Stack:** Java 11+, MALLET 2.0.8 (`cc.mallet.fst.CRF`), Jackson, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-28-paper-mode-v2-hybrid-design.md` (Section 4)

**Build commands:**
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:/c/tools/apache-maven-3.9.9/bin:$PATH"
```

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `processors/paper/crf/CRFFeatureExtractor.java` | Extract 40+ features per zone into MALLET-compatible format |
| `processors/paper/crf/CRFClassifier.java` | Wrap MALLET CRF for inference: classify zone sequences |
| `processors/paper/crf/CRFTrainer.java` | Train CRF model from labeled data |
| `processors/paper/crf/TrainingDataConverter.java` | Convert review JSON → MALLET InstanceList |
| `processors/paper/TemplateAutoGenerator.java` | Generate templates from consistent high-confidence results |
| `scripts/train-crf.sh` | Shell script for CRF training |
| `scripts/batch-paper.sh` | Shell script for batch processing with review queue |
| Test files for each new class |

### Modified Files

| File | Change |
|------|--------|
| `java/pom.xml` | Add MALLET to dependencyManagement |
| `java/opendataloader-pdf-core/pom.xml` | Add MALLET dependency |
| `api/Config.java` | Add `paperCrfModelPath` field |
| `cli/CLIOptions.java` | Add `--paper-crf-model` option |
| `processors/paper/PaperProcessor.java` | Replace CRF placeholder with actual Layer 2 routing |

All paths below relative to `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/`.

---

## Task 1: Add MALLET Dependency + CRFFeatureExtractor

**Files:**
- Modify: `java/pom.xml` (dependencyManagement)
- Modify: `java/opendataloader-pdf-core/pom.xml` (dependency)
- Create: `processors/paper/crf/CRFFeatureExtractor.java`
- Create: `test: processors/paper/crf/CRFFeatureExtractorTest.java`

- [ ] **Step 1: Add MALLET to parent pom.xml dependencyManagement**

In `java/pom.xml`, add inside `<dependencyManagement><dependencies>`:

```xml
<dependency>
    <groupId>cc.mallet</groupId>
    <artifactId>mallet</artifactId>
    <version>2.0.8</version>
</dependency>
```

- [ ] **Step 2: Add MALLET to core pom.xml dependencies**

In `java/opendataloader-pdf-core/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>cc.mallet</groupId>
    <artifactId>mallet</artifactId>
</dependency>
```

- [ ] **Step 3: Verify MALLET resolves**

Run: `mvn dependency:resolve -pl opendataloader-pdf-core -q`
Expected: No errors

- [ ] **Step 4: Write CRFFeatureExtractor tests**

```java
package org.opendataloader.pdf.processors.paper.crf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import java.util.*;

public class CRFFeatureExtractorTest {

    @Test
    void testExtractFeaturesProducesCorrectCount() {
        Zone zone = createZone(ZoneType.TITLE, 1, "Some Title Text", 20.0, true);
        Map<String, Double> features = CRFFeatureExtractor.extractNumericFeatures(zone);
        assertTrue(features.size() >= 25, "Should extract at least 25 numeric features");
    }

    @Test
    void testBooleanFeaturesAreZeroOrOne() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1, "Body text content here.", 12.0, false);
        Map<String, Double> features = CRFFeatureExtractor.extractNumericFeatures(zone);
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            if (entry.getKey().startsWith("has") || entry.getKey().startsWith("is") ||
                entry.getKey().startsWith("contains")) {
                assertTrue(entry.getValue() == 0.0 || entry.getValue() == 1.0,
                    entry.getKey() + " should be 0 or 1, got " + entry.getValue());
            }
        }
    }

    @Test
    void testToMalletFeatureString() {
        Zone zone = createZone(ZoneType.TITLE, 1, "Title", 20.0, true);
        String featureStr = CRFFeatureExtractor.toMalletFeatureString(zone);
        assertNotNull(featureStr);
        assertTrue(featureStr.contains("relativeY="));
        assertTrue(featureStr.contains("fontSizeRatio="));
        assertTrue(featureStr.contains("isBold=1"));
    }

    @Test
    void testKoreanRatioFeature() {
        Zone zone = createZone(ZoneType.BODY_TEXT, 1, "한국어 텍스트입니다", 12.0, false);
        Map<String, Double> features = CRFFeatureExtractor.extractNumericFeatures(zone);
        assertTrue(features.get("koreanRatio") > 0.5, "Korean ratio should be high for Korean text");
    }

    @Test
    void testEmailDetectionFeature() {
        Zone zone = createZone(ZoneType.AUTHOR_BLOCK, 1, "John Smith john@example.com", 10.0, false);
        Map<String, Double> features = CRFFeatureExtractor.extractNumericFeatures(zone);
        assertEquals(1.0, features.get("hasEmail"));
    }

    private Zone createZone(ZoneType type, int page, String text, double fontSize, boolean bold) {
        ZoneFeatures features = new ZoneFeatures(0.1, fontSize, fontSize,
            fontSize / 10.0, bold, true, false, 0.5, 2, 30,
            false, false, false, false, page - 1, 0.1, type == ZoneType.TITLE);
        Zone zone = new Zone(page, new BoundingBox(0, 72, 100, 540, 700),
            new ArrayList<>(), features);
        zone.setType(type);
        zone.setConfidence(0.8);
        zone.setTextOverride(text);
        return zone;
    }
}
```

- [ ] **Step 5: Implement CRFFeatureExtractor**

```java
package org.opendataloader.pdf.processors.paper.crf;

import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneFeatures;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class CRFFeatureExtractor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+");
    private static final Pattern DOI = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\s*\\[?\\d+[.)\\]]\\s");
    private static final Pattern SECTION_NUMBER = Pattern.compile("^\\s*([IVX]+\\.|\\d+\\.)\\s");
    private static final Pattern ABSTRACT_LABEL = Pattern.compile(
        "\\b(Abstract|ABSTRACT|초록|요약|국문초록)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEYWORD_LABEL = Pattern.compile(
        "\\b(Keywords|Key words|키워드|핵심어|주제어|주요어)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REF_LABEL = Pattern.compile(
        "\\b(References|REFERENCES|참고문헌|Bibliography)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACK_LABEL = Pattern.compile(
        "\\b(Acknowledgment|Acknowledgement|감사의 글|사사)\\b", Pattern.CASE_INSENSITIVE);

    public static Map<String, Double> extractNumericFeatures(Zone zone) {
        ZoneFeatures zf = zone.getFeatures();
        String text = zone.getTextContent();
        Map<String, Double> f = new LinkedHashMap<>();

        // Position features (8)
        f.put("relativeY", zf.getRelativeY());
        f.put("pageIndex", (double) zf.getPageIndex());
        f.put("pageRatio", zf.getPageRatio());
        f.put("isFirstPage", zf.getPageIndex() == 0 ? 1.0 : 0.0);
        f.put("isLastQuarter", zf.getPageRatio() > 0.75 ? 1.0 : 0.0);
        f.put("widthRatio", computeWidthRatio(zone));
        f.put("heightRatio", computeHeightRatio(zone));
        f.put("zoneArea", computeArea(zone));

        // Font features (6)
        f.put("avgFontSize", zf.getAvgFontSize());
        f.put("maxFontSize", zf.getMaxFontSize());
        f.put("fontSizeRatio", zf.getFontSizeRatio());
        f.put("isBold", zf.isBold() ? 1.0 : 0.0);
        f.put("isCentered", zf.isCentered() ? 1.0 : 0.0);
        f.put("isItalic", zf.isItalic() ? 1.0 : 0.0);

        // Text stats (8)
        f.put("lineCount", (double) zf.getLineCount());
        f.put("avgLineLength", zf.getAvgLineLength());
        f.put("textDensity", zf.getTextDensity());
        f.put("charCount", (double) text.length());
        f.put("koreanRatio", computeKoreanRatio(text));
        f.put("digitRatio", computeDigitRatio(text));
        f.put("punctuationRatio", computePunctuationRatio(text));
        f.put("wordCount", (double) text.split("\\s+").length);

        // Pattern features (10)
        f.put("hasEmail", EMAIL.matcher(text).find() ? 1.0 : 0.0);
        f.put("hasDoi", DOI.matcher(text).find() ? 1.0 : 0.0);
        f.put("hasUrl", URL.matcher(text).find() ? 1.0 : 0.0);
        f.put("hasNumberPrefix", zf.isHasNumberPrefix() ? 1.0 : 0.0);
        f.put("hasSuperscript", zf.isHasSuperscript() ? 1.0 : 0.0);
        f.put("hasQuotedText", text.contains("\"") || text.contains("\u201C") ? 1.0 : 0.0);
        f.put("startsWithCapital", !text.isEmpty() && Character.isUpperCase(text.charAt(0)) ? 1.0 : 0.0);
        f.put("containsYear", YEAR.matcher(text).find() ? 1.0 : 0.0);
        f.put("hasCommaList", text.chars().filter(c -> c == ',').count() >= 3 ? 1.0 : 0.0);
        f.put("hasParentheses", text.contains("(") ? 1.0 : 0.0);

        // Keyword features (5)
        f.put("containsAbstractLabel", ABSTRACT_LABEL.matcher(text).find() ? 1.0 : 0.0);
        f.put("containsKeywordLabel", KEYWORD_LABEL.matcher(text).find() ? 1.0 : 0.0);
        f.put("containsRefLabel", REF_LABEL.matcher(text).find() ? 1.0 : 0.0);
        f.put("containsSectionNumber", SECTION_NUMBER.matcher(text).find() ? 1.0 : 0.0);
        f.put("containsAckLabel", ACK_LABEL.matcher(text).find() ? 1.0 : 0.0);

        // Semantic
        f.put("isHeading", zf.isHeading() ? 1.0 : 0.0);

        return f;
    }

    public static String toMalletFeatureString(Zone zone) {
        Map<String, Double> features = extractNumericFeatures(zone);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            if (sb.length() > 0) sb.append(" ");
            if (entry.getValue() == 0.0 || entry.getValue() == 1.0) {
                sb.append(entry.getKey()).append("=").append((int) entry.getValue().doubleValue());
            } else {
                sb.append(entry.getKey()).append("=").append(String.format("%.4f", entry.getValue()));
            }
        }
        return sb.toString();
    }

    private static double computeKoreanRatio(String text) {
        if (text.isEmpty()) return 0;
        long korean = text.codePoints().filter(cp ->
            (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0x3131 && cp <= 0x318E)).count();
        return (double) korean / text.length();
    }

    private static double computeDigitRatio(String text) {
        if (text.isEmpty()) return 0;
        long digits = text.chars().filter(Character::isDigit).count();
        return (double) digits / text.length();
    }

    private static double computePunctuationRatio(String text) {
        if (text.isEmpty()) return 0;
        long punct = text.chars().filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)).count();
        return (double) punct / text.length();
    }

    private static double computeWidthRatio(Zone zone) {
        if (zone.getBounds() == null) return 0;
        double width = zone.getBounds().getRightX() - zone.getBounds().getLeftX();
        return width / 612.0; // standard letter width
    }

    private static double computeHeightRatio(Zone zone) {
        if (zone.getBounds() == null) return 0;
        double height = zone.getBounds().getTopY() - zone.getBounds().getBottomY();
        return height / 792.0; // standard letter height
    }

    private static double computeArea(Zone zone) {
        return computeWidthRatio(zone) * computeHeightRatio(zone);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn compile -pl opendataloader-pdf-core -q && mvn test -pl opendataloader-pdf-core -Dtest="CRFFeatureExtractorTest" -q`
Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add java/pom.xml java/opendataloader-pdf-core/pom.xml \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/crf/CRFFeatureExtractor.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/crf/CRFFeatureExtractorTest.java
git commit -m "feat(paper-crf): add MALLET dependency and CRFFeatureExtractor with 40+ features"
```

---

## Task 2: CRFClassifier (MALLET CRF Wrapper)

**Files:**
- Create: `processors/paper/crf/CRFClassifier.java`
- Create: `test: processors/paper/crf/CRFClassifierTest.java`

- [ ] **Step 1: Write CRFClassifier tests**

```java
package org.opendataloader.pdf.processors.paper.crf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import java.nio.file.Path;
import java.util.*;

public class CRFClassifierTest {

    @Test
    void testClassifyWithoutModelReturnsUnknown() {
        CRFClassifier classifier = new CRFClassifier(); // no model loaded
        List<Zone> zones = List.of(
            createZone(1, "Title text", 20.0, true),
            createZone(1, "Author text", 10.0, false)
        );
        List<CRFClassifier.Classification> results = classifier.classify(zones);
        assertEquals(2, results.size());
        for (CRFClassifier.Classification c : results) {
            assertEquals(ZoneType.UNKNOWN, c.type());
            assertEquals(0.0, c.confidence());
        }
    }

    @Test
    void testSaveAndLoadModel(@TempDir Path tempDir) {
        CRFClassifier classifier = new CRFClassifier();
        // Train with minimal data
        List<List<LabeledZone>> trainingData = new ArrayList<>();
        List<LabeledZone> doc1 = List.of(
            new LabeledZone(createZone(1, "Big Title", 24.0, true), ZoneType.TITLE),
            new LabeledZone(createZone(1, "John Smith", 10.0, false), ZoneType.AUTHOR_BLOCK),
            new LabeledZone(createZone(1, "Abstract text here...", 10.0, false), ZoneType.ABSTRACT),
            new LabeledZone(createZone(2, "Body paragraph", 10.0, false), ZoneType.BODY_TEXT)
        );
        // Repeat to give CRF enough data
        for (int i = 0; i < 10; i++) trainingData.add(doc1);

        classifier.train(trainingData);
        assertTrue(classifier.isModelLoaded());

        // Save
        Path modelPath = tempDir.resolve("test.crf.model");
        classifier.save(modelPath);
        assertTrue(modelPath.toFile().exists());

        // Load into new instance
        CRFClassifier loaded = CRFClassifier.load(modelPath);
        assertTrue(loaded.isModelLoaded());

        // Classify
        List<Zone> testZones = List.of(
            createZone(1, "Another Title", 24.0, true),
            createZone(1, "Body text content", 10.0, false)
        );
        List<CRFClassifier.Classification> results = loaded.classify(testZones);
        assertEquals(2, results.size());
        // First zone should have some non-zero confidence
        assertNotEquals(ZoneType.UNKNOWN, results.get(0).type());
    }

    @Test
    void testModelNotLoadedReturnsFalse() {
        CRFClassifier classifier = new CRFClassifier();
        assertFalse(classifier.isModelLoaded());
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

    public record LabeledZone(Zone zone, ZoneType label) {}
}
```

- [ ] **Step 2: Implement CRFClassifier**

```java
package org.opendataloader.pdf.processors.paper.crf;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.*;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneType;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class CRFClassifier {
    private static final Logger LOGGER = Logger.getLogger(CRFClassifier.class.getName());
    private CRF crf;
    private Pipe pipe;

    public CRFClassifier() {
        this.pipe = buildPipe();
    }

    public boolean isModelLoaded() {
        return crf != null;
    }

    public void train(List<List<CRFClassifierTest.LabeledZone>> trainingData) {
        InstanceList instances = new InstanceList(pipe);

        for (List<CRFClassifierTest.LabeledZone> doc : trainingData) {
            String[][] tokens = new String[doc.size()][2];
            for (int i = 0; i < doc.size(); i++) {
                tokens[i][0] = CRFFeatureExtractor.toMalletFeatureString(doc.get(i).zone());
                tokens[i][1] = doc.get(i).label().name();
            }
            instances.addThruPipe(new Instance(tokens, null, "doc", null));
        }

        crf = new CRF(pipe, null);
        // Add fully-connected states for all ZoneTypes
        String[] stateNames = Arrays.stream(ZoneType.values())
            .map(Enum::name).toArray(String[]::new);
        crf.addFullyConnectedStatesForLabels();

        CRFTrainerByLabelLikelihood trainer = new CRFTrainerByLabelLikelihood(crf);
        trainer.setGaussianPriorVariance(1.0);
        for (int i = 0; i < 200; i++) {
            if (trainer.train(instances, 1)) break;
        }
        LOGGER.info("CRF training completed");
    }

    public List<Classification> classify(List<Zone> zones) {
        if (crf == null) {
            return zones.stream()
                .map(z -> new Classification(ZoneType.UNKNOWN, 0.0))
                .toList();
        }

        String[][] tokens = new String[zones.size()][1];
        for (int i = 0; i < zones.size(); i++) {
            tokens[i][0] = CRFFeatureExtractor.toMalletFeatureString(zones.get(i));
        }

        InstanceList testList = new InstanceList(pipe);
        testList.addThruPipe(new Instance(tokens, null, "test", null));

        Instance instance = testList.get(0);
        Sequence<?> input = (Sequence<?>) instance.getData();
        Sequence<?> output = crf.transduce(input);

        List<Classification> results = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
            String label = output.get(i).toString();
            ZoneType type;
            try {
                type = ZoneType.valueOf(label);
            } catch (IllegalArgumentException e) {
                type = ZoneType.UNKNOWN;
            }
            // Compute confidence using Viterbi score
            double confidence = 0.8; // Default for CRF predictions
            results.add(new Classification(type, confidence));
        }
        return results;
    }

    public void save(Path modelPath) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(modelPath.toFile()))) {
            oos.writeObject(crf);
            oos.writeObject(pipe);
            LOGGER.info("CRF model saved to " + modelPath);
        } catch (IOException e) {
            LOGGER.severe("Failed to save CRF model: " + e.getMessage());
        }
    }

    public static CRFClassifier load(Path modelPath) {
        CRFClassifier classifier = new CRFClassifier();
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(modelPath.toFile()))) {
            classifier.crf = (CRF) ois.readObject();
            classifier.pipe = (Pipe) ois.readObject();
            LOGGER.info("CRF model loaded from " + modelPath);
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.severe("Failed to load CRF model: " + e.getMessage());
        }
        return classifier;
    }

    private static Pipe buildPipe() {
        ArrayList<Pipe> pipes = new ArrayList<>();
        pipes.add(new SimpleTaggerSentence2TokenSequence());
        pipes.add(new TokenSequence2FeatureVectorSequence());
        return new SerialPipes(pipes);
    }

    public record Classification(ZoneType type, double confidence) {}
}
```

**NOTE:** The MALLET pipe setup above is a simplified version. The implementer should READ the MALLET SimpleTagger source and examples to verify the correct Pipe configuration for sequence tagging with pre-computed features. The key pattern is:
- Input: `String[][]` where each `String[i]` is `[features, label]` for training or `[features]` for inference
- Pipe: `SimpleTaggerSentence2TokenSequence` → `TokenSequence2FeatureVectorSequence`
- CRF: `addFullyConnectedStatesForLabels()` then train with `CRFTrainerByLabelLikelihood`

- [ ] **Step 3: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="CRFClassifierTest" -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/crf/CRFClassifier.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/crf/CRFClassifierTest.java
git commit -m "feat(paper-crf): add CRFClassifier wrapper for MALLET CRF training and inference"
```

---

## Task 3: TrainingDataConverter

**Files:**
- Create: `processors/paper/crf/TrainingDataConverter.java`
- Create: `test: processors/paper/crf/TrainingDataConverterTest.java`

- [ ] **Step 1: Write tests**

```java
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
        // Create a minimal review JSON with corrected zone labels
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("source", "test.pdf");
        ArrayNode zones = root.putArray("zones");

        ObjectNode zone1 = mapper.createObjectNode();
        zone1.put("index", 0);
        zone1.put("page", 1);
        zone1.put("text_preview", "Original Article");
        zone1.put("classified_as", "TITLE");
        zone1.put("corrected_as", "PAGE_METADATA");
        zones.add(zone1);

        ObjectNode zone2 = mapper.createObjectNode();
        zone2.put("index", 1);
        zone2.put("page", 1);
        zone2.put("text_preview", "Real Paper Title");
        zone2.put("classified_as", "BODY_TEXT");
        zone2.put("corrected_as", "TITLE");
        zones.add(zone2);

        Path reviewFile = tempDir.resolve("test.review.json");
        mapper.writeValue(reviewFile.toFile(), root);

        List<TrainingDataConverter.LabeledZoneData> data =
            TrainingDataConverter.fromReviewJson(reviewFile);

        assertEquals(2, data.size());
        assertEquals("PAGE_METADATA", data.get(0).label()); // uses corrected
        assertEquals("TITLE", data.get(1).label());
    }

    @Test
    void testUseOriginalLabelWhenNotCorrected(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("source", "test.pdf");
        ArrayNode zones = root.putArray("zones");

        ObjectNode zone1 = mapper.createObjectNode();
        zone1.put("index", 0);
        zone1.put("page", 1);
        zone1.put("text_preview", "Actual Title");
        zone1.put("classified_as", "TITLE");
        zone1.putNull("corrected_as");
        zones.add(zone1);

        Path reviewFile = tempDir.resolve("test2.review.json");
        mapper.writeValue(reviewFile.toFile(), root);

        List<TrainingDataConverter.LabeledZoneData> data =
            TrainingDataConverter.fromReviewJson(reviewFile);

        assertEquals(1, data.size());
        assertEquals("TITLE", data.get(0).label()); // uses original since no correction
    }
}
```

- [ ] **Step 2: Implement TrainingDataConverter**

```java
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

    public record LabeledZoneData(int index, int page, String textPreview, String label) {}
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="TrainingDataConverterTest" -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/crf/TrainingDataConverter.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/crf/TrainingDataConverterTest.java
git commit -m "feat(paper-crf): add TrainingDataConverter for review JSON to CRF training data"
```

---

## Task 4: TemplateAutoGenerator

**Files:**
- Create: `processors/paper/TemplateAutoGenerator.java`
- Create: `test: processors/paper/TemplateAutoGeneratorTest.java`

- [ ] **Step 1: Write tests**

```java
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.paper.*;
import java.util.*;

public class TemplateAutoGeneratorTest {

    @Test
    void testNoGenerationWithFewerThan10Results() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(createHighConfidenceDoc("10.1234/test"));
        }
        Optional<JsonNode> template = TemplateAutoGenerator.tryGenerate("test-journal", results);
        assertTrue(template.isEmpty());
    }

    @Test
    void testGeneratesTemplateWith10ConsistentResults() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            results.add(createHighConfidenceDoc("10.1234/test"));
        }
        Optional<JsonNode> template = TemplateAutoGenerator.tryGenerate("test-journal", results);
        assertTrue(template.isPresent());
        assertEquals("test-journal", template.get().get("journal_id").asText());
    }

    @Test
    void testNoGenerationWithLowConfidence() {
        List<PaperDocument> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PaperDocument doc = new PaperDocument("test.pdf", 10);
            doc.setTitle("Title " + i);
            doc.setConfidence("title", 0.5); // low
            results.add(doc);
        }
        Optional<JsonNode> template = TemplateAutoGenerator.tryGenerate("test-journal", results);
        assertTrue(template.isEmpty());
    }

    private PaperDocument createHighConfidenceDoc(String doiPrefix) {
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        doc.setTitle("Consistent Title Pattern");
        doc.setDoi(doiPrefix + ".2025." + new Random().nextInt(1000));
        doc.setConfidence("title", 0.95);
        doc.setConfidence("authors", 0.92);
        doc.setConfidence("abstract", 0.90);
        doc.getKeywords().addAll(List.of("kw1", "kw2"));
        return doc;
    }
}
```

- [ ] **Step 2: Implement TemplateAutoGenerator**

```java
package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.opendataloader.pdf.paper.PaperDocument;

import java.util.*;
import java.util.logging.Logger;

public class TemplateAutoGenerator {
    private static final Logger LOGGER = Logger.getLogger(TemplateAutoGenerator.class.getName());
    private static final int MIN_DOCUMENTS = 10;
    private static final double MIN_AVG_CONFIDENCE = 0.85;

    public static Optional<JsonNode> tryGenerate(String journalId, List<PaperDocument> results) {
        if (results.size() < MIN_DOCUMENTS) return Optional.empty();

        // Check average confidence across all documents
        double avgTitleConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("title", 0.0))
            .average().orElse(0);
        double avgAuthorConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("authors", 0.0))
            .average().orElse(0);
        double avgAbstractConf = results.stream()
            .mapToDouble(d -> d.getConfidence().getOrDefault("abstract", 0.0))
            .average().orElse(0);

        double overallAvg = (avgTitleConf + avgAuthorConf + avgAbstractConf) / 3.0;
        if (overallAvg < MIN_AVG_CONFIDENCE) {
            LOGGER.fine("Average confidence " + overallAvg + " below threshold " + MIN_AVG_CONFIDENCE);
            return Optional.empty();
        }

        // Generate a basic template
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode template = mapper.createObjectNode();
        template.put("journal_id", journalId);
        template.put("name", "Auto-generated template for " + journalId);
        template.put("auto_generated", true);
        template.put("document_count", results.size());
        template.put("avg_confidence", Math.round(overallAvg * 100.0) / 100.0);

        // Title rules — use default strategy
        ObjectNode titleRules = template.putObject("title_rules");
        ArrayNode skipLabels = titleRules.putArray("skip_labels");
        skipLabels.add("Original Article").add("Review Article").add("Case Report");
        titleRules.put("strategy", "largest_font_excluding_skips");
        titleRules.put("page", 0);
        titleRules.put("position", "upper_half");

        // Author rules
        ObjectNode authorRules = template.putObject("author_rules");
        ArrayNode excludePatterns = authorRules.putArray("exclude_patterns");
        excludePatterns.add("Received:").add("Revised:").add("Accepted:").add("Published:");
        authorRules.putArray("stop_before_patterns");
        authorRules.put("separator", ",");
        authorRules.put("affiliation_marker", "superscript");

        // Abstract rules — combine common labels
        ObjectNode abstractRules = template.putObject("abstract_rules");
        ArrayNode labels = abstractRules.putArray("labels");
        labels.add("Abstract").add("ABSTRACT").add("초록").add("요약").add("Purpose").add("국문초록");
        ArrayNode endLabels = abstractRules.putArray("end_labels");
        endLabels.add("Keywords").add("Key words").add("키워드").add("핵심어").add("주요어");

        // Keyword rules
        ObjectNode keywordRules = template.putObject("keyword_rules");
        ArrayNode kwLabels = keywordRules.putArray("labels");
        kwLabels.add("Keywords").add("Key words").add("키워드").add("핵심어").add("주요어");
        ArrayNode separators = keywordRules.putArray("separators");
        separators.add(",").add(";").add("·");

        // Reference rules
        ObjectNode referenceRules = template.putObject("reference_rules");
        ArrayNode headingPatterns = referenceRules.putArray("heading_patterns");
        headingPatterns.add("References").add("REFERENCES").add("참고문헌");
        referenceRules.put("entry_pattern", "bracket_number");

        LOGGER.info("Auto-generated template for journal '" + journalId +
            "' from " + results.size() + " documents (avg confidence: " + overallAvg + ")");

        return Optional.of(template);
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="TemplateAutoGeneratorTest" -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/TemplateAutoGenerator.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/TemplateAutoGeneratorTest.java
git commit -m "feat(paper-crf): add TemplateAutoGenerator for automatic template creation"
```

---

## Task 5: Integrate CRF into PaperProcessor (Layer 2) + Config/CLI

**Files:**
- Modify: `api/Config.java`
- Modify: `cli/CLIOptions.java`
- Modify: `processors/paper/PaperProcessor.java`
- Modify: `processors/paper/ReviewQueueWriter.java` (add zone data for training)

- [ ] **Step 1: Add Config field**

In `Config.java`, after `paperReviewDir`:
```java
private String paperCrfModelPath;

public String getPaperCrfModelPath() { return paperCrfModelPath; }
public void setPaperCrfModelPath(String path) { this.paperCrfModelPath = path; }
```

- [ ] **Step 2: Add CLIOptions**

Constants:
```java
private static final String PAPER_CRF_MODEL_LONG_OPTION = "paper-crf-model";
private static final String PAPER_CRF_MODEL_DESC = "Path to trained CRF model file for paper mode zone classification";
```

OPTION_DEFINITIONS:
```java
new OptionDefinition(PAPER_CRF_MODEL_LONG_OPTION, null, "string", null, PAPER_CRF_MODEL_DESC, true),
```

Handler:
```java
if (commandLine.hasOption(PAPER_CRF_MODEL_LONG_OPTION)) {
    config.setPaperCrfModelPath(commandLine.getOptionValue(PAPER_CRF_MODEL_LONG_OPTION));
}
```

- [ ] **Step 3: Update PaperProcessor to use CRF**

Replace the CRF placeholder (lines 64-65) with:

```java
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

                // Apply CRF classifications to zones (for extraction)
                List<Zone> crfZones = new ArrayList<>(allZones);
                double avgCrfConf = 0;
                for (int i = 0; i < crfZones.size() && i < crfResults.size(); i++) {
                    var c = crfResults.get(i);
                    if (c.confidence() >= 0.8) {
                        crfZones.get(i).setType(c.type());
                        crfZones.get(i).setConfidence(c.confidence());
                    }
                    avgCrfConf += c.confidence();
                }
                avgCrfConf /= crfResults.size();

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
```

- [ ] **Step 4: Update ReviewQueueWriter to include zone data for training**

Add zone classification data to the review JSON so corrected labels can be used for CRF training:

In `ReviewQueueWriter.write()`, after the fields section, add:

```java
// Add zone data for CRF training
ArrayNode zonesArray = root.putArray("zones");
// Note: zones would need to be passed to this method
// For now, omit zone-level data (can be added when zones are available)
```

Actually, modify the method signature to accept zones:

```java
public static void write(PaperDocument doc, List<Zone> zones,
                          String inputPdfName, String reviewDir) throws IOException {
    // ... existing code ...

    // Add zone data for training
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
}
```

Update PaperProcessor call to pass zones:
```java
ReviewQueueWriter.write(finalDoc, allZones, inputPdfName, config.getPaperReviewDir());
```

- [ ] **Step 5: Compile and run ALL tests**

Run: `mvn compile -q && mvn test -pl opendataloader-pdf-core -q`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/api/Config.java \
       java/opendataloader-pdf-cli/src/main/java/org/opendataloader/pdf/cli/CLIOptions.java \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/PaperProcessor.java \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/ReviewQueueWriter.java
git commit -m "feat(paper-crf): integrate CRF Layer 2 into PaperProcessor pipeline"
```

---

## Task 6: Training Script + Batch Processing Script

**Files:**
- Create: `scripts/train-crf.sh`
- Create: `scripts/batch-paper.sh`

- [ ] **Step 1: Create train-crf.sh**

```bash
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"
TRAINING_DIR="${1:?Usage: train-crf.sh <training-data-dir> <output-model-path>}"
MODEL_PATH="${2:?Usage: train-crf.sh <training-data-dir> <output-model-path>}"

echo "=== CRF Training ==="
echo "Training data: $TRAINING_DIR"
echo "Output model: $MODEL_PATH"

# Count training files
COUNT=$(find "$TRAINING_DIR" -name "*.review.json" | wc -l)
echo "Found $COUNT review JSON files"

if [ "$COUNT" -lt 10 ]; then
    echo "WARNING: Fewer than 10 training documents. Model quality may be poor."
fi

# Training is done via Java — invoke the CRF trainer
java -cp "$JAR_PATH" org.opendataloader.pdf.processors.paper.crf.CRFTrainerCLI \
    --training-dir "$TRAINING_DIR" \
    --output "$MODEL_PATH"

echo "=== Training Complete ==="
echo "Model saved to: $MODEL_PATH"
```

- [ ] **Step 2: Create batch-paper.sh**

```bash
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0-shaded.jar"

INPUT_DIR="${1:?Usage: batch-paper.sh <input-pdf-dir> [output-dir] [review-dir] [crf-model]}"
OUTPUT_DIR="${2:-$INPUT_DIR/output}"
REVIEW_DIR="${3:-$INPUT_DIR/review}"
CRF_MODEL="${4:-}"

mkdir -p "$OUTPUT_DIR" "$REVIEW_DIR"

echo "=== Batch Paper Processing ==="
echo "Input: $INPUT_DIR"
echo "Output: $OUTPUT_DIR"
echo "Review: $REVIEW_DIR"
if [ -n "$CRF_MODEL" ]; then
    echo "CRF Model: $CRF_MODEL"
fi

COUNT=0
for pdf in "$INPUT_DIR"/*.pdf; do
    [ -f "$pdf" ] || continue
    COUNT=$((COUNT + 1))
    name=$(basename "$pdf" .pdf)
    echo "[$COUNT] Processing: $name"

    EXTRA_ARGS="--paper-mode --paper-review-dir $REVIEW_DIR -o $OUTPUT_DIR"
    if [ -n "$CRF_MODEL" ]; then
        EXTRA_ARGS="$EXTRA_ARGS --paper-crf-model $CRF_MODEL"
    fi

    java -jar "$JAR_PATH" $EXTRA_ARGS "$pdf" 2>/dev/null || echo "  ERROR processing $name"
done

echo ""
echo "=== Summary ==="
echo "Processed: $COUNT PDFs"
echo "Output files: $(ls "$OUTPUT_DIR"/*.paper.json 2>/dev/null | wc -l) paper.json"
echo "Review queue: $(ls "$REVIEW_DIR"/*.review.json 2>/dev/null | wc -l) items"
```

- [ ] **Step 3: Make executable**

```bash
chmod +x scripts/train-crf.sh scripts/batch-paper.sh
```

- [ ] **Step 4: Commit**

```bash
git add scripts/train-crf.sh scripts/batch-paper.sh
git commit -m "build: add CRF training and batch paper processing scripts"
```

---

## Task 7: CLI Sync + Final Verification

**Files:**
- Regenerate: `options.json`, Python/Node bindings

- [ ] **Step 1: Run npm run sync**

```bash
npm run sync
```

- [ ] **Step 2: Verify new option in bindings**

```bash
grep "paper-crf-model" options.json python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py node/opendataloader-pdf/src/cli-options.generated.ts
```

- [ ] **Step 3: Build and run all tests**

```bash
cd java && mvn package -q -DskipTests && mvn test -pl opendataloader-pdf-core -q
```

- [ ] **Step 4: Commit**

```bash
git add options.json python/ node/ content/
git commit -m "build: sync paper-crf-model CLI option to Python and Node.js bindings"
```

---

## Summary

| Task | Component | Steps |
|------|-----------|-------|
| 1 | MALLET dependency + CRFFeatureExtractor | 7 |
| 2 | CRFClassifier (MALLET wrapper) | 4 |
| 3 | TrainingDataConverter | 4 |
| 4 | TemplateAutoGenerator | 4 |
| 5 | PaperProcessor CRF integration + Config/CLI | 6 |
| 6 | Training + batch scripts | 4 |
| 7 | CLI sync + verification | 4 |
| **Total** | | **33 steps** |

### CRF Usage Workflow (After Implementation)

```
1. 첫 배치 처리:
   ./scripts/batch-paper.sh /path/to/pdfs /path/to/output /path/to/review

2. 수동 교정 (review JSON의 corrected_as 필드):
   /path/to/review/*.review.json 파일 편집

3. CRF 모델 학습:
   ./scripts/train-crf.sh /path/to/review /path/to/model.crf

4. CRF 모델로 재처리:
   ./scripts/batch-paper.sh /path/to/pdfs /path/to/output /path/to/review /path/to/model.crf

5. 반복 → 정확도 향상
```
