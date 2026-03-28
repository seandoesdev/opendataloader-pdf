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
