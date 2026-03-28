package org.opendataloader.pdf.paper.output;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PaperOutputTest {

    private PaperDocument buildTestDoc() {
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        doc.setTitle("Test Paper Title");
        doc.setAbstractText("This is the abstract.");
        doc.setDoi("10.1234/test.2026");
        doc.getAuthors().add(new PaperAuthor("John Smith", "MIT", "john@mit.edu", true));
        doc.getKeywords().addAll(Arrays.asList("AI", "NLP"));
        PaperPublication pub = new PaperPublication();
        pub.setVenue("AI Journal");
        pub.setVolume("53");
        doc.setPublication(pub);
        doc.getSections().add(new PaperSection("introduction", "1. Introduction", "Intro text.", 1, 2));
        PaperReference ref = new PaperReference(1, "[1] Author. Title. 2024.");
        ref.setTitle("Ref Title");
        ref.setYear(2024);
        doc.getReferences().add(ref);
        return doc;
    }

    @Test
    void testJsonOutput() throws IOException {
        PaperDocument doc = buildTestDoc();
        Path tempDir = Files.createTempDirectory("paper-test");
        try {
            PaperJsonWriter.write(doc, "test.pdf", tempDir.toString());
            Path jsonFile = tempDir.resolve("test.paper.json");
            assertTrue(Files.exists(jsonFile), "JSON file should exist");
            String content = Files.readString(jsonFile);
            assertTrue(content.contains("\"title\" : \"Test Paper Title\""), "Should contain title");
            assertTrue(content.contains("\"abstract\" : \"This is the abstract.\""), "Should contain abstract");
            assertTrue(content.contains("\"doi\" : \"10.1234/test.2026\""), "Should contain DOI");
            assertTrue(content.contains("\"name\" : \"John Smith\""), "Should contain author name");
            assertTrue(content.contains("\"AI\""), "Should contain keyword AI");
            assertTrue(content.contains("\"NLP\""), "Should contain keyword NLP");
            assertTrue(content.contains("\"venue\" : \"AI Journal\""), "Should contain venue");
            assertTrue(content.contains("\"volume\" : \"53\""), "Should contain volume");
            assertTrue(content.contains("\"type\" : \"introduction\""), "Should contain section type");
            assertTrue(content.contains("\"title\" : \"Ref Title\""), "Should contain ref title");
            assertTrue(content.contains("\"source_file\" : \"test.pdf\""), "Should contain source file");
            assertTrue(content.contains("\"total_pages\" : 10"), "Should contain total pages");
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void testMarkdownOutput() throws IOException {
        PaperDocument doc = buildTestDoc();
        Path tempDir = Files.createTempDirectory("paper-test");
        try {
            PaperMarkdownGenerator.write(doc, "test.pdf", tempDir.toString());
            Path mdFile = tempDir.resolve("test.paper.md");
            assertTrue(Files.exists(mdFile), "Markdown file should exist");
            String content = Files.readString(mdFile);
            assertTrue(content.startsWith("---\n"), "Should start with YAML frontmatter");
            assertTrue(content.contains("title: \"Test Paper Title\""), "Should contain title");
            assertTrue(content.contains("doi: \"10.1234/test.2026\""), "Should contain DOI");
            assertTrue(content.contains("  - name: John Smith"), "Should contain author");
            assertTrue(content.contains("keywords: [AI, NLP]"), "Should contain keywords");
            assertTrue(content.contains("## Abstract"), "Should contain abstract heading");
            assertTrue(content.contains("This is the abstract."), "Should contain abstract text");
            assertTrue(content.contains("## 1. Introduction"), "Should contain section heading");
            assertTrue(content.contains("Intro text."), "Should contain section content");
            assertTrue(content.contains("## References"), "Should contain references heading");
            assertTrue(content.contains("[^1]:"), "Should contain reference footnote");
            assertTrue(content.contains("\"Ref Title.\""), "Should contain formatted ref title");
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void testEmptyDocument() throws IOException {
        PaperDocument doc = new PaperDocument("empty.pdf", 0);
        Path tempDir = Files.createTempDirectory("paper-test");
        try {
            assertDoesNotThrow(() -> PaperJsonWriter.write(doc, "empty.pdf", tempDir.toString()));
            assertDoesNotThrow(() -> PaperMarkdownGenerator.write(doc, "empty.pdf", tempDir.toString()));
            assertTrue(Files.exists(tempDir.resolve("empty.paper.json")), "JSON file should exist");
            assertTrue(Files.exists(tempDir.resolve("empty.paper.md")), "Markdown file should exist");
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.collect(Collectors.toList())) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
