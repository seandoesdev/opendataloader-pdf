package org.opendataloader.pdf.processors.paper;

import org.opendataloader.pdf.paper.PaperDocument;

public class ResultMerger {

    public static PaperDocument merge(PaperDocument templateResult,
                                       PaperDocument crfResult,
                                       PaperDocument ruleResult) {
        PaperDocument base = ruleResult != null ? ruleResult : new PaperDocument("unknown", 0);
        PaperDocument merged = new PaperDocument(base.getSourceFile(), base.getTotalPages());

        // Title — pick highest confidence
        pickBestString(merged, "title", templateResult, crfResult, ruleResult);
        pickBestString(merged, "title_en", templateResult, crfResult, ruleResult);
        pickBestString(merged, "abstract", templateResult, crfResult, ruleResult);
        pickBestString(merged, "doi", templateResult, crfResult, ruleResult);

        // Authors — from highest confidence layer
        PaperDocument bestAuthors = pickBestDoc("authors", templateResult, crfResult, ruleResult);
        if (bestAuthors != null && !bestAuthors.getAuthors().isEmpty()) {
            merged.getAuthors().addAll(bestAuthors.getAuthors());
        }

        // Keywords
        PaperDocument bestKw = pickBestDoc("keywords", templateResult, crfResult, ruleResult);
        if (bestKw != null && !bestKw.getKeywords().isEmpty()) {
            merged.getKeywords().addAll(bestKw.getKeywords());
        }

        // Publication
        PaperDocument bestPub = pickBestDoc("publication", templateResult, crfResult, ruleResult);
        if (bestPub != null && bestPub.getPublication() != null) {
            merged.setPublication(bestPub.getPublication());
        }

        // Sections — always from rule result (template doesn't do sections differently)
        if (ruleResult != null) merged.getSections().addAll(ruleResult.getSections());

        // References — from highest confidence
        PaperDocument bestRefs = pickBestDoc("references", templateResult, crfResult, ruleResult);
        if (bestRefs != null && !bestRefs.getReferences().isEmpty()) {
            merged.getReferences().addAll(bestRefs.getReferences());
        }

        // Unlinked citations
        if (ruleResult != null) merged.getUnlinkedCitations().addAll(ruleResult.getUnlinkedCitations());

        // Language
        merged.setLanguage(coalesce(
            templateResult != null ? templateResult.getLanguage() : null,
            crfResult != null ? crfResult.getLanguage() : null,
            ruleResult != null ? ruleResult.getLanguage() : null));

        // Merge confidence maps (keep highest per field)
        mergeConfidence(merged, templateResult);
        mergeConfidence(merged, crfResult);
        mergeConfidence(merged, ruleResult);

        return merged;
    }

    private static void pickBestString(PaperDocument merged, String field,
                                        PaperDocument... docs) {
        String bestValue = null;
        double bestConf = -1;
        for (PaperDocument doc : docs) {
            if (doc == null) continue;
            String value = getStringField(doc, field);
            double conf = doc.getConfidence().getOrDefault(field, 0.0);
            if (value != null && conf > bestConf) {
                bestValue = value;
                bestConf = conf;
            }
        }
        if (bestValue != null) {
            setStringField(merged, field, bestValue);
            merged.setConfidence(field, bestConf);
        }
    }

    private static PaperDocument pickBestDoc(String field, PaperDocument... docs) {
        PaperDocument best = null;
        double bestConf = -1;
        for (PaperDocument doc : docs) {
            if (doc == null) continue;
            double conf = doc.getConfidence().getOrDefault(field, 0.0);
            if (conf > bestConf) { best = doc; bestConf = conf; }
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

    private static void setStringField(PaperDocument doc, String field, String value) {
        switch (field) {
            case "title": doc.setTitle(value); break;
            case "title_en": doc.setTitleEn(value); break;
            case "abstract": doc.setAbstractText(value); break;
            case "doi": doc.setDoi(value); break;
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
