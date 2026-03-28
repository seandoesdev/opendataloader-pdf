package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.PaperDocument;

import java.util.List;
import java.util.logging.Logger;

public class PaperZoneLogger {
    private static final Logger LOGGER = Logger.getLogger(PaperZoneLogger.class.getName());

    public static void log(List<Zone> zones, PaperDocument doc) {
        for (Zone zone : zones) {
            LOGGER.fine(String.format("[PAPER-ZONE] Page %d: type=%s confidence=%.2f text='%.50s'",
                zone.getPageNumber(), zone.getType(), zone.getConfidence(),
                zone.getTextContent().replace('\n', ' ')));
        }
        LOGGER.info(String.format("[PAPER] title=%s, authors=%d, abstract=%s, doi=%s, refs=%d, sections=%d",
            doc.getTitle() != null ? "yes" : "no",
            doc.getAuthors().size(),
            doc.getAbstractText() != null ? "yes" : "no",
            doc.getDoi() != null ? "yes" : "no",
            doc.getReferences().size(),
            doc.getSections().size()));
    }
}
