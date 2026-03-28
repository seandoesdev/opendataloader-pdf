package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MetadataExtractor {
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[^\\s,;}\\]]+");
    private static final Pattern KEYWORD_LABEL = Pattern.compile(
        "(Keywords|Key words|KEYWORDS|키워드|핵심어|주제어)\\s*[:\\.]?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOLUME_PATTERN = Pattern.compile("Vol\\.?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISSUE_PATTERN = Pattern.compile("No\\.?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGES_PATTERN = Pattern.compile("pp\\.?\\s*(\\d+)\\s*[-\u2013]\\s*(\\d+)");
    private static final Pattern KOREAN_VOLUME = Pattern.compile("\uc81c(\\d+)\uad8c");
    private static final Pattern KOREAN_ISSUE = Pattern.compile("\uc81c(\\d+)\ud638");
    private static final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");

    public static void extract(List<Zone> zones, PaperDocument doc) {
        for (Zone zone : zones) {
            String text = zone.getTextContent().trim();
            if (zone.getType() == ZoneType.PAGE_METADATA) {
                extractDoi(text, doc);
                extractPublication(text, doc);
            }
            if (zone.getType() == ZoneType.KEYWORDS) {
                extractKeywords(text, doc);
            }
        }
    }

    private static void extractDoi(String text, PaperDocument doc) {
        if (doc.getDoi() != null) return;
        Matcher m = DOI_PATTERN.matcher(text);
        if (m.find()) doc.setDoi(m.group());
    }

    private static void extractKeywords(String text, PaperDocument doc) {
        Matcher labelMatcher = KEYWORD_LABEL.matcher(text);
        if (labelMatcher.find()) text = text.substring(labelMatcher.end()).trim();
        String[] parts = text.split("[,;\u00b7]");
        List<String> keywords = Arrays.stream(parts)
            .map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        if (!keywords.isEmpty()) doc.getKeywords().addAll(keywords);
    }

    private static void extractPublication(String text, PaperDocument doc) {
        PaperPublication pub = doc.getPublication();
        if (pub == null) { pub = new PaperPublication(); doc.setPublication(pub); }

        Matcher vm = VOLUME_PATTERN.matcher(text);
        if (vm.find()) pub.setVolume(vm.group(1));
        Matcher kvm = KOREAN_VOLUME.matcher(text);
        if (kvm.find() && pub.getVolume() == null) pub.setVolume(kvm.group(1));

        Matcher im = ISSUE_PATTERN.matcher(text);
        if (im.find()) pub.setIssue(im.group(1));
        Matcher kim = KOREAN_ISSUE.matcher(text);
        if (kim.find() && pub.getIssue() == null) pub.setIssue(kim.group(1));

        Matcher pm = PAGES_PATTERN.matcher(text);
        if (pm.find()) pub.setPages(pm.group(1) + "-" + pm.group(2));

        Matcher ym = YEAR_PATTERN.matcher(text);
        if (ym.find() && pub.getDate() == null) pub.setDate(ym.group());
    }
}
