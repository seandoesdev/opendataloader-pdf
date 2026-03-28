package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.PaperDocument;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneType;
import java.util.List;

public class TitleExtractor {
    public static void extract(List<Zone> zones, PaperDocument doc) {
        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.TITLE) {
                String text = zone.getTextContent().trim();
                if (text.isEmpty()) continue;
                if (!PaperValidator.isValidTitle(text)) continue;  // Skip category labels

                boolean hasKorean = text.codePoints().anyMatch(cp ->
                    (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0x3131 && cp <= 0x318E));

                doc.setTitle(text);
                doc.setLanguage(hasKorean ? "ko" : "en");
                doc.setConfidence("title", zone.getConfidence());
                break;
            }
        }
    }
}
