package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PaperProcessorIntegrationTest {

    // paper_test PDFs are in the project root
    private static final Path PROJECT_ROOT = Paths.get("../../");

    @Test
    void testPaperTest1(@TempDir Path tempDir) throws IOException {
        runPaperTest("paper_test1.pdf", tempDir);
    }

    @Test
    void testPaperTest2(@TempDir Path tempDir) throws IOException {
        runPaperTest("paper_test2.pdf", tempDir);
    }

    @Test
    void testPaperTest3(@TempDir Path tempDir) throws IOException {
        runPaperTest("paper_test3.pdf", tempDir);
    }

    private void runPaperTest(String fileName, Path tempDir) throws IOException {
        Path pdfPath = PROJECT_ROOT.resolve(fileName);
        if (!Files.exists(pdfPath)) {
            System.out.println("Skipping " + fileName + " - file not found at " + pdfPath.toAbsolutePath());
            return;
        }

        Config config = new Config();
        config.setPaperMode(true);
        config.setOutputFolder(tempDir.toString());

        DocumentProcessor.processFile(pdfPath.toAbsolutePath().toString(), config);

        String baseName = fileName.replace(".pdf", "");

        // Verify paper.json was created
        Path paperJson = tempDir.resolve(baseName + ".paper.json");
        Assertions.assertTrue(Files.exists(paperJson), "paper.json should be generated for " + fileName);

        // Verify paper.md was created
        Path paperMd = tempDir.resolve(baseName + ".paper.md");
        Assertions.assertTrue(Files.exists(paperMd), "paper.md should be generated for " + fileName);

        // Read and validate JSON has key fields
        String json = Files.readString(paperJson);
        Assertions.assertTrue(json.contains("\"title\""), "JSON should contain title field");
        Assertions.assertTrue(json.contains("\"authors\""), "JSON should contain authors field");
        Assertions.assertTrue(json.contains("\"metadata\""), "JSON should contain metadata field");

        // Read and validate Markdown has frontmatter
        String md = Files.readString(paperMd);
        Assertions.assertTrue(md.startsWith("---"), "Markdown should start with YAML frontmatter");
        Assertions.assertTrue(md.contains("title:"), "Markdown should contain title in frontmatter");

        // Log results for review
        System.out.println("=== " + fileName + " ===");
        System.out.println("JSON size: " + json.length() + " bytes");
        System.out.println("MD size: " + md.length() + " bytes");

        // Print key fields from JSON for verification
        if (json.contains("\"title\"")) {
            int titleStart = json.indexOf("\"title\"") + 10;
            int titleEnd = json.indexOf("\"", titleStart);
            if (titleEnd > titleStart) {
                System.out.println("Title: " + json.substring(titleStart, Math.min(titleEnd, titleStart + 100)));
            }
        }
    }
}
