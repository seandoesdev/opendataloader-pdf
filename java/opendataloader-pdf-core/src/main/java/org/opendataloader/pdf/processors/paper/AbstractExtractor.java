package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.PaperDocument;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractExtractor {
    private static final Pattern ABSTRACT_LABEL = Pattern.compile(
        "^\\s*(Abstract|ABSTRACT|초록|요약|요 약)\\s*[:\\.]?\\s*", Pattern.CASE_INSENSITIVE);

    public static void extract(List<Zone> zones, PaperDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (Zone zone : zones) {
            if (zone.getType() == ZoneType.ABSTRACT) {
                String text = zone.getTextContent().trim();
                Matcher matcher = ABSTRACT_LABEL.matcher(text);
                if (matcher.find()) {
                    text = text.substring(matcher.end()).trim();
                }
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(text);
                }
            }
        }
        if (sb.length() > 0) {
            doc.setAbstractText(sb.toString());
            doc.setConfidence("abstract", zones.stream()
                .filter(z -> z.getType() == ZoneType.ABSTRACT)
                .mapToDouble(Zone::getConfidence).average().orElse(0));
        }
    }
}
