# Paper Mode v2 Sub-project 1: Template DB + Validator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add journal-specific template matching and result validation to paper mode, achieving 90-95% accuracy on registered journals.

**Architecture:** JournalFingerprinter identifies the journal from first-page text (DOI/ISSN/name). TemplateRegistry loads journal-specific extraction rules. TemplateBasedExtractor applies those rules. PaperValidator cross-checks results. PaperProcessor is updated to try template extraction first, falling back to the existing ZoneClassifier-based pipeline. ReviewQueueWriter outputs low-confidence results for human review.

**Tech Stack:** Java 11+, Jackson (JSON template loading), JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-28-paper-mode-v2-hybrid-design.md`

**Build commands (set up environment first):**
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:/c/tools/apache-maven-3.9.9/bin:$PATH"
```

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `processors/paper/JournalFingerprinter.java` | Identify journal from first-page text via DOI prefix, ISSN, or name patterns |
| `processors/paper/TemplateRegistry.java` | Load, cache, and look up journal templates from JSON files |
| `processors/paper/TemplateBasedExtractor.java` | Extract paper metadata using journal-specific template rules |
| `processors/paper/PaperValidator.java` | Validate extracted fields (title, authors, DOI, abstract, year) |
| `processors/paper/ResultMerger.java` | Merge multi-layer results by picking highest-confidence per field |
| `processors/paper/ReviewQueueWriter.java` | Write low-confidence results to review queue JSON |
| `resources/paper-templates/_registry.json` | Journal fingerprint database |
| `resources/paper-templates/cacd.json` | Template for paper_test1 journal |
| `resources/paper-templates/ksepe.json` | Template for paper_test3 journal |
| `resources/paper-templates/default.json` | Fallback template for unknown journals |
| Test files for each new class |

### Modified Files

| File | Change |
|------|--------|
| `Config.java` | Add `paperTemplateDir`, `paperReviewDir` fields |
| `CLIOptions.java` | Add `--paper-template-dir`, `--paper-review-dir` options |
| `PaperProcessor.java` | Add template-first routing with fallback |
| `TitleExtractor.java` | Integrate PaperValidator |
| `AuthorExtractor.java` | Add exclude patterns + PaperValidator |

All paths below are relative to `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/`.

---

## Task 1: PaperValidator

**Files:**
- Create: `processors/paper/PaperValidator.java`
- Create: `test: processors/paper/PaperValidatorTest.java`

- [ ] **Step 1: Write failing tests**

```java
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PaperValidatorTest {

    @Test
    void testValidTitle() {
        assertTrue(PaperValidator.isValidTitle("Speech Processing in Children"));
        assertTrue(PaperValidator.isValidTitle("딥러닝 기반 자연어 처리 연구"));
    }

    @Test
    void testInvalidTitleCategoryLabel() {
        assertFalse(PaperValidator.isValidTitle("Original Article"));
        assertFalse(PaperValidator.isValidTitle("Review Article"));
        assertFalse(PaperValidator.isValidTitle("Case Report"));
        assertFalse(PaperValidator.isValidTitle("Brief Report"));
        assertFalse(PaperValidator.isValidTitle("Short Communication"));
    }

    @Test
    void testInvalidTitleTooShort() {
        assertFalse(PaperValidator.isValidTitle("Hi"));
        assertFalse(PaperValidator.isValidTitle(null));
        assertFalse(PaperValidator.isValidTitle(""));
    }

    @Test
    void testValidAuthor() {
        assertTrue(PaperValidator.isValidAuthor("홍길동"));
        assertTrue(PaperValidator.isValidAuthor("John Smith"));
        assertTrue(PaperValidator.isValidAuthor("Deok Gi Chae"));
    }

    @Test
    void testInvalidAuthorDateText() {
        assertFalse(PaperValidator.isValidAuthor("Received: 1 August"));
        assertFalse(PaperValidator.isValidAuthor("2025. Revised: 6 September"));
        assertFalse(PaperValidator.isValidAuthor("Accepted: 6 September"));
        assertFalse(PaperValidator.isValidAuthor("Published: March 2025"));
    }

    @Test
    void testInvalidAuthorTooShort() {
        assertFalse(PaperValidator.isValidAuthor("A"));
        assertFalse(PaperValidator.isValidAuthor(null));
        assertFalse(PaperValidator.isValidAuthor("123"));
    }

    @Test
    void testValidDoi() {
        assertTrue(PaperValidator.isValidDoi("10.21849/cacd.2025.10.3.1"));
        assertTrue(PaperValidator.isValidDoi("10.26844/ksepe.2025.31.4.1"));
    }

    @Test
    void testInvalidDoi() {
        assertFalse(PaperValidator.isValidDoi("not-a-doi"));
        assertFalse(PaperValidator.isValidDoi(null));
        assertFalse(PaperValidator.isValidDoi(""));
    }

    @Test
    void testValidAbstract() {
        assertTrue(PaperValidator.isValidAbstract(
            "This study examines the speech processing abilities of children with phonological disorders compared to typically developing children."));
    }

    @Test
    void testInvalidAbstractTooShort() {
        assertFalse(PaperValidator.isValidAbstract("Short."));
        assertFalse(PaperValidator.isValidAbstract(null));
    }

    @Test
    void testValidYear() {
        assertTrue(PaperValidator.isValidYear(2025));
        assertTrue(PaperValidator.isValidYear(1990));
    }

    @Test
    void testInvalidYear() {
        assertFalse(PaperValidator.isValidYear(1800));
        assertFalse(PaperValidator.isValidYear(2099));
        assertFalse(PaperValidator.isValidYear(null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="PaperValidatorTest" -q`
Expected: FAIL — PaperValidator not found

- [ ] **Step 3: Implement PaperValidator**

```java
package org.opendataloader.pdf.processors.paper;

import java.util.Set;

public class PaperValidator {

    private static final Set<String> CATEGORY_LABELS = Set.of(
        "Original Article", "Review Article", "Case Report", "Brief Report",
        "Short Communication", "Research Article", "Letter to the Editor",
        "Editorial", "Commentary", "Erratum", "Corrigendum",
        "원저", "종설", "증례보고", "단보");

    public static boolean isValidTitle(String title) {
        if (title == null || title.trim().length() < 5) return false;
        if (title.length() > 500) return false;
        if (CATEGORY_LABELS.contains(title.trim())) return false;
        return true;
    }

    public static boolean isValidAuthor(String name) {
        if (name == null || name.trim().length() < 2 || name.length() > 100) return false;
        if (name.matches(".*\\b(Received|Revised|Accepted|Published|Submitted)\\b.*")) return false;
        if (name.matches(".*\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\b.*")) return false;
        if (name.matches("^[\\d\\s.,;:]+$")) return false;
        if (name.matches("^\\d{4}\\..*")) return false;
        return true;
    }

    public static boolean isValidDoi(String doi) {
        return doi != null && doi.matches("10\\.\\d{4,9}/\\S+");
    }

    public static boolean isValidAbstract(String text) {
        return text != null && text.trim().length() >= 50;
    }

    public static boolean isValidYear(Integer year) {
        return year != null && year >= 1900 && year <= java.time.Year.now().getValue() + 2;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="PaperValidatorTest" -q`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/PaperValidator.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/PaperValidatorTest.java
git commit -m "feat(paper-v2): add PaperValidator for field-level cross-checking"
```

---

## Task 2: Template JSON Files + TemplateRegistry

**Files:**
- Create: `resources/paper-templates/_registry.json`
- Create: `resources/paper-templates/cacd.json`
- Create: `resources/paper-templates/ksepe.json`
- Create: `resources/paper-templates/default.json`
- Create: `processors/paper/TemplateRegistry.java`
- Create: `test: processors/paper/TemplateRegistryTest.java`

- [ ] **Step 1: Create _registry.json**

```json
{
  "journals": [
    {
      "journal_id": "cacd",
      "doi_prefixes": ["10.21849/cacd"],
      "issn": ["2586-7792"],
      "name_patterns": ["Commun Sci & Disord", "Communication Sciences & Disorders"]
    },
    {
      "journal_id": "ksepe",
      "doi_prefixes": ["10.26844/ksepe"],
      "issn": [],
      "name_patterns": ["한국초등체육학회지", "Korean Journal of Elementary Physical Education"]
    }
  ]
}
```

- [ ] **Step 2: Create cacd.json (paper_test1 journal)**

```json
{
  "journal_id": "cacd",
  "name": "Communication Sciences & Disorders",
  "title_rules": {
    "skip_labels": ["Original Article", "Review Article", "Brief Report", "Case Report"],
    "strategy": "largest_font_excluding_skips",
    "page": 0,
    "position": "upper_half"
  },
  "author_rules": {
    "exclude_patterns": ["Received:", "Revised:", "Accepted:", "Published:"],
    "stop_before_patterns": ["^Department", "^School", "^College"],
    "separator": ",",
    "affiliation_marker": "superscript"
  },
  "abstract_rules": {
    "labels": ["Purpose", "Abstract", "Background", "Objectives"],
    "end_labels": ["Keywords", "Key words", "KEYWORDS"]
  },
  "keyword_rules": {
    "labels": ["Keywords", "Key words"],
    "separators": [",", ";"]
  },
  "reference_rules": {
    "heading_patterns": ["References", "REFERENCES"],
    "entry_pattern": "bracket_number"
  }
}
```

- [ ] **Step 3: Create ksepe.json (paper_test3 journal)**

```json
{
  "journal_id": "ksepe",
  "name": "한국초등체육학회지",
  "title_rules": {
    "skip_labels": [],
    "strategy": "largest_font_in_body",
    "page": 0,
    "position": "upper_half"
  },
  "author_rules": {
    "exclude_patterns": ["국문초록", "Abstract"],
    "stop_before_patterns": ["국문초록", "Abstract", "^\\d+\\)"],
    "separator": ",",
    "affiliation_marker": "parentheses"
  },
  "abstract_rules": {
    "labels": ["국문초록", "초록", "Abstract"],
    "end_labels": ["주요어", "핵심어", "키워드", "Keywords"]
  },
  "keyword_rules": {
    "labels": ["주요어", "핵심어", "키워드", "Keywords"],
    "separators": [",", ";", "·"]
  },
  "reference_rules": {
    "heading_patterns": ["참고문헌", "참고 문헌", "References"],
    "entry_pattern": "dot_number"
  }
}
```

- [ ] **Step 4: Create default.json**

```json
{
  "journal_id": "default",
  "name": "Default Template",
  "title_rules": {
    "skip_labels": ["Original Article", "Review Article", "Brief Report", "Case Report", "Short Communication", "Research Article"],
    "strategy": "largest_font_excluding_skips",
    "page": 0,
    "position": "upper_half"
  },
  "author_rules": {
    "exclude_patterns": ["Received:", "Revised:", "Accepted:", "Published:"],
    "stop_before_patterns": [],
    "separator": ",",
    "affiliation_marker": "superscript"
  },
  "abstract_rules": {
    "labels": ["Abstract", "ABSTRACT", "초록", "요약", "Purpose", "Background", "Objectives", "국문초록"],
    "end_labels": ["Keywords", "Key words", "KEYWORDS", "키워드", "핵심어", "주제어", "주요어"]
  },
  "keyword_rules": {
    "labels": ["Keywords", "Key words", "KEYWORDS", "키워드", "핵심어", "주제어", "주요어"],
    "separators": [",", ";", "·"]
  },
  "reference_rules": {
    "heading_patterns": ["References", "REFERENCES", "참고문헌", "참고 문헌", "Bibliography"],
    "entry_pattern": "bracket_number"
  }
}
```

- [ ] **Step 5: Write TemplateRegistry tests**

```java
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;

public class TemplateRegistryTest {

    @Test
    void testLoadDefaultRegistry() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        assertNotNull(registry);
    }

    @Test
    void testFindByDoiPrefix() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplate("10.21849/cacd.2025.10.3.1");
        assertNotNull(template);
        assertEquals("cacd", template.get("journal_id").asText());
    }

    @Test
    void testFindByNamePattern() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplateByText("한국초등체육학회지, 2025, 제31권");
        assertNotNull(template);
        assertEquals("ksepe", template.get("journal_id").asText());
    }

    @Test
    void testUnknownJournalReturnsDefault() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplate("10.9999/unknown.2025");
        assertNotNull(template);
        assertEquals("default", template.get("journal_id").asText());
    }
}
```

- [ ] **Step 6: Implement TemplateRegistry**

```java
package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class TemplateRegistry {
    private static final Logger LOGGER = Logger.getLogger(TemplateRegistry.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<JournalEntry> entries;
    private final Map<String, JsonNode> templateCache;
    private final JsonNode defaultTemplate;
    private final String templateDir; // null = classpath

    private TemplateRegistry(List<JournalEntry> entries, Map<String, JsonNode> templateCache,
                              JsonNode defaultTemplate, String templateDir) {
        this.entries = entries;
        this.templateCache = templateCache;
        this.defaultTemplate = defaultTemplate;
        this.templateDir = templateDir;
    }

    public static TemplateRegistry loadDefault() {
        return loadFromClasspath();
    }

    public static TemplateRegistry loadFromDir(String dir) {
        try {
            JsonNode registry = MAPPER.readTree(Files.readString(Path.of(dir, "_registry.json")));
            List<JournalEntry> entries = parseEntries(registry);
            Map<String, JsonNode> cache = new HashMap<>();
            for (JournalEntry entry : entries) {
                Path tplPath = Path.of(dir, entry.journalId + ".json");
                if (Files.exists(tplPath)) {
                    cache.put(entry.journalId, MAPPER.readTree(Files.readString(tplPath)));
                }
            }
            JsonNode defaultTpl = MAPPER.readTree(Files.readString(Path.of(dir, "default.json")));
            cache.put("default", defaultTpl);
            return new TemplateRegistry(entries, cache, defaultTpl, dir);
        } catch (IOException e) {
            LOGGER.warning("Failed to load templates from " + dir + ": " + e.getMessage());
            return loadFromClasspath();
        }
    }

    private static TemplateRegistry loadFromClasspath() {
        try {
            InputStream regStream = TemplateRegistry.class.getResourceAsStream("/paper-templates/_registry.json");
            JsonNode registry = MAPPER.readTree(regStream);
            List<JournalEntry> entries = parseEntries(registry);
            Map<String, JsonNode> cache = new HashMap<>();
            for (JournalEntry entry : entries) {
                InputStream tplStream = TemplateRegistry.class.getResourceAsStream(
                    "/paper-templates/" + entry.journalId + ".json");
                if (tplStream != null) {
                    cache.put(entry.journalId, MAPPER.readTree(tplStream));
                }
            }
            InputStream defaultStream = TemplateRegistry.class.getResourceAsStream("/paper-templates/default.json");
            JsonNode defaultTpl = MAPPER.readTree(defaultStream);
            cache.put("default", defaultTpl);
            return new TemplateRegistry(entries, cache, defaultTpl, null);
        } catch (IOException e) {
            LOGGER.warning("Failed to load default templates: " + e.getMessage());
            return new TemplateRegistry(Collections.emptyList(), Collections.emptyMap(), null, null);
        }
    }

    /** Find template by DOI string (e.g., "10.21849/cacd.2025.10.3.1") */
    public JsonNode findTemplate(String doi) {
        if (doi != null) {
            for (JournalEntry entry : entries) {
                for (String prefix : entry.doiPrefixes) {
                    if (doi.startsWith(prefix)) {
                        return templateCache.getOrDefault(entry.journalId, defaultTemplate);
                    }
                }
            }
        }
        return defaultTemplate;
    }

    /** Find template by scanning text for ISSN or journal name */
    public JsonNode findTemplateByText(String text) {
        if (text == null) return defaultTemplate;
        for (JournalEntry entry : entries) {
            for (String issn : entry.issn) {
                if (text.contains(issn)) {
                    return templateCache.getOrDefault(entry.journalId, defaultTemplate);
                }
            }
            for (String pattern : entry.namePatterns) {
                if (text.contains(pattern)) {
                    return templateCache.getOrDefault(entry.journalId, defaultTemplate);
                }
            }
        }
        return defaultTemplate;
    }

    private static List<JournalEntry> parseEntries(JsonNode registry) {
        List<JournalEntry> entries = new ArrayList<>();
        for (JsonNode journal : registry.get("journals")) {
            JournalEntry entry = new JournalEntry();
            entry.journalId = journal.get("journal_id").asText();
            entry.doiPrefixes = jsonArrayToList(journal.get("doi_prefixes"));
            entry.issn = jsonArrayToList(journal.get("issn"));
            entry.namePatterns = jsonArrayToList(journal.get("name_patterns"));
            entries.add(entry);
        }
        return entries;
    }

    private static List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) list.add(item.asText());
        }
        return list;
    }

    static class JournalEntry {
        String journalId;
        List<String> doiPrefixes;
        List<String> issn;
        List<String> namePatterns;
    }
}
```

- [ ] **Step 7: Run tests**

Run: `mvn compile -pl opendataloader-pdf-core -q && mvn test -pl opendataloader-pdf-core -Dtest="TemplateRegistryTest" -q`
Expected: All PASS

- [ ] **Step 8: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/resources/paper-templates/ \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/TemplateRegistry.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/TemplateRegistryTest.java
git commit -m "feat(paper-v2): add TemplateRegistry with journal fingerprint matching"
```

---

## Task 3: JournalFingerprinter

**Files:**
- Create: `processors/paper/JournalFingerprinter.java`
- Create: `test: processors/paper/JournalFingerprinterTest.java`

- [ ] **Step 1: Write failing tests**

```java
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JournalFingerprinterTest {

    @Test
    void testIdentifyByDoi() {
        String text = "DOI: 10.21849/cacd.2025.10.3.1\nSome other text";
        String journalId = JournalFingerprinter.identify(text, TemplateRegistry.loadDefault());
        assertEquals("cacd", journalId);
    }

    @Test
    void testIdentifyByName() {
        String text = "한국초등체육학회지, 2025, 제31권, 제4호";
        String journalId = JournalFingerprinter.identify(text, TemplateRegistry.loadDefault());
        assertEquals("ksepe", journalId);
    }

    @Test
    void testUnknownJournal() {
        String text = "Some random text with no journal info";
        String journalId = JournalFingerprinter.identify(text, TemplateRegistry.loadDefault());
        assertEquals("default", journalId);
    }

    @Test
    void testExtractDoi() {
        String text = "https://doi.org/10.21849/cacd.2025.10.3.1 Published 2025";
        String doi = JournalFingerprinter.extractDoi(text);
        assertEquals("10.21849/cacd.2025.10.3.1", doi);
    }
}
```

- [ ] **Step 2: Implement JournalFingerprinter**

```java
package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JournalFingerprinter {
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");

    public static String identify(String firstPageText, TemplateRegistry registry) {
        // 1. Try DOI prefix matching
        String doi = extractDoi(firstPageText);
        if (doi != null) {
            JsonNode template = registry.findTemplate(doi);
            if (template != null) {
                return template.get("journal_id").asText();
            }
        }
        // 2. Try ISSN / name pattern matching
        JsonNode template = registry.findTemplateByText(firstPageText);
        if (template != null) {
            return template.get("journal_id").asText();
        }
        return "default";
    }

    public static String extractDoi(String text) {
        if (text == null) return null;
        Matcher m = DOI_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="JournalFingerprinterTest" -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/JournalFingerprinter.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/JournalFingerprinterTest.java
git commit -m "feat(paper-v2): add JournalFingerprinter for DOI/ISSN/name-based journal identification"
```

---

## Task 4: TemplateBasedExtractor

**Files:**
- Create: `processors/paper/TemplateBasedExtractor.java`
- Create: `test: processors/paper/TemplateBasedExtractorTest.java`

- [ ] **Step 1: Write failing tests**

```java
package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class TemplateBasedExtractorTest {

    @Test
    void testSkipsCategoryLabel() {
        // Simulates paper_test1: "Original Article" is the first zone but should be skipped
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.TITLE, 1, "Original Article", 12.0, true));
        zones.add(createZone(ZoneType.TITLE, 1, "Speech Processing Abilities in Children with Phonological Disorders", 20.0, true));

        JsonNode template = loadTemplate("cacd.json");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals("Speech Processing Abilities in Children with Phonological Disorders", doc.getTitle());
        assertTrue(doc.getConfidence().getOrDefault("title", 0.0) >= 0.9);
    }

    @Test
    void testExcludesDateFromAuthors() {
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.AUTHOR_BLOCK, 1,
            "Received: 1 August, 2025. Revised: 6 September, 2025. Deok Gi Chae, Eun Kyoung Lee", 10.0, false));

        JsonNode template = loadTemplate("cacd.json");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        // "Received..." and "Revised..." should be excluded
        for (PaperAuthor author : doc.getAuthors()) {
            assertFalse(author.getName().contains("Received"));
            assertFalse(author.getName().contains("Revised"));
        }
        assertTrue(doc.getAuthors().size() >= 1);
    }

    @Test
    void testAbstractExtraction() {
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.ABSTRACT, 1,
            "Purpose This study examined speech processing abilities.", 10.0, false));

        JsonNode template = loadTemplate("cacd.json");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertNotNull(doc.getAbstractText());
        assertFalse(doc.getAbstractText().startsWith("Purpose"));
        assertTrue(doc.getAbstractText().contains("speech processing"));
    }

    @Test
    void testReferenceEntryPattern() {
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.REFERENCE_BODY, 10,
            "[1] Kim, S. \"Title one.\" Journal A, 2024. [2] Lee, J. \"Title two.\" Journal B, 2023.", 10.0, false));

        JsonNode template = loadTemplate("cacd.json");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals(2, doc.getReferences().size());
    }

    @Test
    void testDefaultTemplateAlsoWorks() {
        List<Zone> zones = new ArrayList<>();
        zones.add(createZone(ZoneType.TITLE, 1, "Original Article", 12.0, true));
        zones.add(createZone(ZoneType.TITLE, 1, "A Study on Something Important", 20.0, true));

        JsonNode template = loadTemplate("default.json");
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        TemplateBasedExtractor.extract(zones, template, doc);

        assertEquals("A Study on Something Important", doc.getTitle());
    }

    private Zone createZone(ZoneType type, int page, String text, double fontSize, boolean bold) {
        ZoneFeatures features = new ZoneFeatures(0.1, fontSize, fontSize,
            fontSize / 10.0, bold, true, false, 0.5, 1, 50,
            false, false, false, false, page - 1, 0.0, false);
        Zone zone = new Zone(page, new org.verapdf.wcag.algorithms.entities.geometry.BoundingBox(
            0, 72, 100, 540, 700), new ArrayList<>(), features);
        zone.setType(type);
        zone.setConfidence(0.8);
        zone.setTextOverride(text);
        return zone;
    }

    private JsonNode loadTemplate(String name) {
        try {
            return new ObjectMapper().readTree(
                getClass().getResourceAsStream("/paper-templates/" + name));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Implement TemplateBasedExtractor**

This class applies template-specific rules to the zones. READ existing TitleExtractor.java, AuthorExtractor.java, AbstractExtractor.java, ReferenceParser.java to reuse their logic but with template-driven configuration.

Key methods:
- `extract(List<Zone> zones, JsonNode template, PaperDocument doc)` — main entry
- `extractTitle(List<Zone> zones, JsonNode titleRules, PaperDocument doc)` — skip labels, find largest font
- `extractAuthors(List<Zone> zones, JsonNode authorRules, PaperDocument doc)` — exclude patterns, validate
- `extractAbstract(List<Zone> zones, JsonNode abstractRules, PaperDocument doc)` — label-based extraction
- `extractReferences(List<Zone> zones, JsonNode refRules, PaperDocument doc)` — entry pattern splitting

Each method calls PaperValidator to filter invalid results and sets confidence 0.90-0.95 for template-matched fields.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="TemplateBasedExtractorTest" -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/TemplateBasedExtractor.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/TemplateBasedExtractorTest.java
git commit -m "feat(paper-v2): add TemplateBasedExtractor with skip-labels and exclude-patterns"
```

---

## Task 5: ResultMerger + ReviewQueueWriter

**Files:**
- Create: `processors/paper/ResultMerger.java`
- Create: `processors/paper/ReviewQueueWriter.java`
- Create: `test: processors/paper/ResultMergerTest.java`

- [ ] **Step 1: Write failing tests**

```java
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResultMergerTest {

    @Test
    void testPicksHigherConfidenceTitle() {
        PaperDocument templateResult = new PaperDocument("test.pdf", 10);
        templateResult.setTitle("Template Title");
        templateResult.setConfidence("title", 0.95);

        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setTitle("Rule Title");
        ruleResult.setConfidence("title", 0.65);

        PaperDocument merged = ResultMerger.merge(templateResult, null, ruleResult);
        assertEquals("Template Title", merged.getTitle());
    }

    @Test
    void testFallsBackToRuleWhenNoTemplate() {
        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setTitle("Rule Title");
        ruleResult.setConfidence("title", 0.65);

        PaperDocument merged = ResultMerger.merge(null, null, ruleResult);
        assertEquals("Rule Title", merged.getTitle());
    }

    @Test
    void testMergesFieldsFromDifferentLayers() {
        PaperDocument templateResult = new PaperDocument("test.pdf", 10);
        templateResult.setTitle("Template Title");
        templateResult.setConfidence("title", 0.95);

        PaperDocument ruleResult = new PaperDocument("test.pdf", 10);
        ruleResult.setDoi("10.1234/test");
        ruleResult.setConfidence("doi", 0.80);

        PaperDocument merged = ResultMerger.merge(templateResult, null, ruleResult);
        assertEquals("Template Title", merged.getTitle());
        assertEquals("10.1234/test", merged.getDoi());
    }
}
```

- [ ] **Step 2: Implement ResultMerger**

```java
package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.PaperDocument;

public class ResultMerger {

    public static PaperDocument merge(PaperDocument templateResult,
                                       PaperDocument crfResult,
                                       PaperDocument ruleResult) {
        PaperDocument base = ruleResult != null ? ruleResult : new PaperDocument("unknown", 0);
        PaperDocument merged = new PaperDocument(base.getSourceFile(), base.getTotalPages());

        // Title
        merged.setTitle(pickBest("title", templateResult, crfResult, ruleResult));
        merged.setTitleEn(pickBest("title_en", templateResult, crfResult, ruleResult));

        // Authors — pick from layer with highest author confidence
        PaperDocument bestAuthors = pickBestDoc("authors", templateResult, crfResult, ruleResult);
        if (bestAuthors != null) merged.getAuthors().addAll(bestAuthors.getAuthors());

        // Abstract
        merged.setAbstractText(pickBest("abstract", templateResult, crfResult, ruleResult));

        // DOI
        merged.setDoi(pickBest("doi", templateResult, crfResult, ruleResult));

        // Keywords — pick from layer with highest keyword confidence
        PaperDocument bestKw = pickBestDoc("keywords", templateResult, crfResult, ruleResult);
        if (bestKw != null) merged.getKeywords().addAll(bestKw.getKeywords());

        // Publication — pick from highest confidence source
        PaperDocument bestPub = pickBestDoc("publication", templateResult, crfResult, ruleResult);
        if (bestPub != null) merged.setPublication(bestPub.getPublication());

        // Sections — from rule result (template doesn't extract sections differently)
        if (ruleResult != null) merged.getSections().addAll(ruleResult.getSections());

        // References — pick from highest confidence source
        PaperDocument bestRefs = pickBestDoc("references", templateResult, crfResult, ruleResult);
        if (bestRefs != null) merged.getReferences().addAll(bestRefs.getReferences());

        // Unlinked citations
        if (ruleResult != null) merged.getUnlinkedCitations().addAll(ruleResult.getUnlinkedCitations());

        // Language
        merged.setLanguage(coalesce(
            templateResult != null ? templateResult.getLanguage() : null,
            crfResult != null ? crfResult.getLanguage() : null,
            ruleResult != null ? ruleResult.getLanguage() : null));

        // Confidence — merge all
        mergeConfidence(merged, templateResult);
        mergeConfidence(merged, crfResult);
        mergeConfidence(merged, ruleResult);

        return merged;
    }

    private static String pickBest(String field, PaperDocument... docs) {
        String best = null;
        double bestConf = -1;
        for (PaperDocument doc : docs) {
            if (doc == null) continue;
            String value = getStringField(doc, field);
            double conf = doc.getConfidence().getOrDefault(field, 0.0);
            if (value != null && conf > bestConf) {
                best = value;
                bestConf = conf;
            }
        }
        return best;
    }

    private static PaperDocument pickBestDoc(String field, PaperDocument... docs) {
        PaperDocument best = null;
        double bestConf = -1;
        for (PaperDocument doc : docs) {
            if (doc == null) continue;
            double conf = doc.getConfidence().getOrDefault(field, 0.0);
            if (conf > bestConf) {
                best = doc;
                bestConf = conf;
            }
        }
        return best;
    }

    private static String getStringField(PaperDocument doc, String field) {
        switch (field) {
            case "title": return doc.getTitle();
            case "title_en": return doc.getTitleEn();
            case "abstract": return doc.getAbstractText();
            case "doi": return doc.getDoi();
            default: return null;
        }
    }

    private static void mergeConfidence(PaperDocument merged, PaperDocument source) {
        if (source == null) return;
        for (var entry : source.getConfidence().entrySet()) {
            double existing = merged.getConfidence().getOrDefault(entry.getKey(), 0.0);
            if (entry.getValue() > existing) {
                merged.setConfidence(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String coalesce(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }
}
```

- [ ] **Step 3: Implement ReviewQueueWriter**

```java
package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;
import org.opendataloader.pdf.paper.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ReviewQueueWriter {
    private static final Logger LOGGER = Logger.getLogger(ReviewQueueWriter.class.getName());

    public static void write(PaperDocument doc, String inputPdfName, String reviewDir) throws IOException {
        if (reviewDir == null) return;

        // Only write if overall confidence is below threshold
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

        String baseName = new File(inputPdfName).getName().replaceFirst("\\.pdf$", "");
        File outputFile = Paths.get(reviewDir, baseName + ".review.json").toFile();
        outputFile.getParentFile().mkdirs();
        mapper.writeValue(outputFile, root);
        LOGGER.info("Review queue written to: " + outputFile.getAbsolutePath());
    }

    private static void addField(ObjectNode fields, String name, String value, double confidence) {
        ObjectNode field = fields.putObject(name);
        if (value != null) field.put("value", value);
        else field.putNull("value");
        field.put("confidence", Math.round(confidence * 100.0) / 100.0);
        field.putNull("corrected");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl opendataloader-pdf-core -Dtest="ResultMergerTest" -q`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/ResultMerger.java \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/ReviewQueueWriter.java \
       java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/paper/ResultMergerTest.java
git commit -m "feat(paper-v2): add ResultMerger and ReviewQueueWriter"
```

---

## Task 6: Update PaperProcessor with Template-First Routing

**Files:**
- Modify: `processors/paper/PaperProcessor.java`
- Modify: `api/Config.java`
- Modify: `cli/CLIOptions.java`

- [ ] **Step 1: Add Config fields**

In `Config.java`, after `paperWeightsPath` (line 87):

```java
private String paperTemplateDir;
private String paperReviewDir;

public String getPaperTemplateDir() { return paperTemplateDir; }
public void setPaperTemplateDir(String paperTemplateDir) { this.paperTemplateDir = paperTemplateDir; }
public String getPaperReviewDir() { return paperReviewDir; }
public void setPaperReviewDir(String paperReviewDir) { this.paperReviewDir = paperReviewDir; }
```

- [ ] **Step 2: Add CLIOptions**

In `CLIOptions.java`, add constants after paper-weights constants (line 143):

```java
private static final String PAPER_TEMPLATE_DIR_LONG_OPTION = "paper-template-dir";
private static final String PAPER_TEMPLATE_DIR_DESC = "Path to custom journal template directory for paper mode";
private static final String PAPER_REVIEW_DIR_LONG_OPTION = "paper-review-dir";
private static final String PAPER_REVIEW_DIR_DESC = "Path to review queue output directory for low-confidence paper mode results";
```

Add to OPTION_DEFINITIONS (after paper-weights entry, line 197):

```java
new OptionDefinition(PAPER_TEMPLATE_DIR_LONG_OPTION, null, "string", null, PAPER_TEMPLATE_DIR_DESC, true),
new OptionDefinition(PAPER_REVIEW_DIR_LONG_OPTION, null, "string", null, PAPER_REVIEW_DIR_DESC, true),
```

Add handlers in createConfigFromCommandLine():

```java
if (commandLine.hasOption(PAPER_TEMPLATE_DIR_LONG_OPTION)) {
    config.setPaperTemplateDir(commandLine.getOptionValue(PAPER_TEMPLATE_DIR_LONG_OPTION));
}
if (commandLine.hasOption(PAPER_REVIEW_DIR_LONG_OPTION)) {
    config.setPaperReviewDir(commandLine.getOptionValue(PAPER_REVIEW_DIR_LONG_OPTION));
}
```

- [ ] **Step 3: Rewrite PaperProcessor with template-first routing**

Replace entire PaperProcessor.java with:

```java
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

        // Classify zones with rule engine (3rd layer — always runs as baseline)
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

        if (!"default".equals(journalId) || true) { // Always try template (even default)
            JsonNode template = registry.findTemplate(
                JournalFingerprinter.extractDoi(firstPageText));
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
        }

        // === Layer 2: CRF (placeholder for Sub-project 2) ===
        PaperDocument crfResult = null;

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
        ReviewQueueWriter.write(finalDoc, inputPdfName, config.getPaperReviewDir());

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
```

- [ ] **Step 4: Compile and run ALL tests**

Run: `mvn compile -q && mvn test -pl opendataloader-pdf-core -q`
Expected: All tests PASS (existing + new)

- [ ] **Step 5: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/PaperProcessor.java \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/api/Config.java \
       java/opendataloader-pdf-cli/src/main/java/org/opendataloader/pdf/cli/CLIOptions.java
git commit -m "feat(paper-v2): update PaperProcessor with template-first routing and review queue"
```

---

## Task 7: Improve TitleExtractor + AuthorExtractor with Validator

**Files:**
- Modify: `processors/paper/TitleExtractor.java`
- Modify: `processors/paper/AuthorExtractor.java`

- [ ] **Step 1: Update TitleExtractor to validate and skip invalid titles**

```java
// In TitleExtractor.extract(), after getting text:
String text = zone.getTextContent().trim();
if (text.isEmpty()) continue;
if (!PaperValidator.isValidTitle(text)) continue;  // Skip category labels
```

- [ ] **Step 2: Update AuthorExtractor to validate each author name**

```java
// In AuthorExtractor, before adding to authors list:
if (name.isEmpty()) continue;
if (!PaperValidator.isValidAuthor(name)) continue;  // Skip dates, numbers
```

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl opendataloader-pdf-core -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/TitleExtractor.java \
       java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/paper/AuthorExtractor.java
git commit -m "feat(paper-v2): integrate PaperValidator into TitleExtractor and AuthorExtractor"
```

---

## Task 8: CLI Sync + Integration Test + Benchmark

**Files:**
- Regenerate: `options.json`, Python/Node bindings
- Modify: `PaperProcessorIntegrationTest.java` (add v2 assertions)

- [ ] **Step 1: Run npm run sync**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:/c/tools/apache-maven-3.9.9/bin:$PATH"
cd /d/projects/opendataloader-pdf && npm run sync
```

- [ ] **Step 2: Build shaded JAR and run benchmark**

```bash
cd /d/projects/opendataloader-pdf/java && mvn package -q -DskipTests
cd /d/projects/opendataloader-pdf && ./scripts/bench-paper.sh
```

Verify paper_test1 no longer has "Original Article" as title and no longer has dates in authors.

- [ ] **Step 3: Commit**

```bash
git add options.json python/ node/ content/
git commit -m "build: sync paper-mode-v2 CLI options and verify benchmark"
```

---

## Summary

| Task | Component | Steps |
|------|-----------|-------|
| 1 | PaperValidator | 5 |
| 2 | Template JSONs + TemplateRegistry | 8 |
| 3 | JournalFingerprinter | 4 |
| 4 | TemplateBasedExtractor | 4 |
| 5 | ResultMerger + ReviewQueueWriter | 5 |
| 6 | PaperProcessor v2 + Config/CLI | 5 |
| 7 | TitleExtractor/AuthorExtractor + Validator | 4 |
| 8 | CLI Sync + Integration + Benchmark | 3 |
| **Total** | | **38 steps** |
