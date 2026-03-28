package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class ReferenceCitationTest {

    private Zone createTextZone(ZoneType type, int page, String text) {
        Zone zone = new Zone(page,
            new BoundingBox(0, 72, 100, 540, 700),
            new ArrayList<>(),
            new ZoneFeatures(0.5, 12, 12, 1.0, false, false, false, 0.5,
                1, 50, false, false, false, false, 0, 0.0, false));
        zone.setType(type);
        zone.setConfidence(0.9);
        zone.setTextOverride(text);
        return zone;
    }

    @Test
    public void testReferenceEntrySplitting() {
        List<Zone> zones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5,
                "[1] Author A. Title One. Journal, 2020.\n[2] Author B. Title Two. Conference, 2021.")
        );
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        ReferenceParser.parse(zones, doc);

        Assertions.assertEquals(2, doc.getReferences().size());
        Assertions.assertEquals(1, doc.getReferences().get(0).getId());
        Assertions.assertEquals(2, doc.getReferences().get(1).getId());
    }

    @Test
    public void testReferenceFieldParsing() {
        List<Zone> zones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5,
                "[1] Kim, Lee, \"Paper Title,\" Journal of AI, 2024.")
        );
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        ReferenceParser.parse(zones, doc);

        Assertions.assertEquals(1, doc.getReferences().size());
        PaperReference ref = doc.getReferences().get(0);
        Assertions.assertEquals("Paper Title,", ref.getTitle());
        Assertions.assertEquals(Integer.valueOf(2024), ref.getYear());
        Assertions.assertTrue(ref.getAuthors().size() >= 2, "Should parse at least 2 authors");
        Assertions.assertTrue(ref.getAuthors().contains("Kim"));
        Assertions.assertTrue(ref.getAuthors().contains("Lee"));
    }

    @Test
    public void testDoiExtractionInReference() {
        List<Zone> zones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5,
                "[1] Smith, J. \"A Study.\" Journal, 2023. 10.1234/abc.2023.001")
        );
        PaperDocument doc = new PaperDocument("test.pdf", 10);
        ReferenceParser.parse(zones, doc);

        Assertions.assertEquals(1, doc.getReferences().size());
        Assertions.assertNotNull(doc.getReferences().get(0).getDoi());
        Assertions.assertTrue(doc.getReferences().get(0).getDoi().startsWith("10.1234/"));
    }

    @Test
    public void testNumericCitationLinking() {
        PaperDocument doc = new PaperDocument("test.pdf", 10);

        // Add a reference with id=1
        List<Zone> refZones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5, "[1] Author A. \"Some Title.\" Journal, 2020.")
        );
        ReferenceParser.parse(refZones, doc);
        Assertions.assertEquals(1, doc.getReferences().size());

        // Body text citing [1]
        List<Zone> bodyZones = List.of(
            createTextZone(ZoneType.BODY_TEXT, 2, "As shown in previous work [1], the method is effective.")
        );
        CitationLinker.link(bodyZones, doc);

        Assertions.assertEquals(1, doc.getReferences().get(0).getCitationsInText().size());
    }

    @Test
    public void testCitationRangeExpansion() {
        List<Integer> ids = CitationLinker.expandCitationRange("1-3");
        Assertions.assertEquals(List.of(1, 2, 3), ids);
    }

    @Test
    public void testUnlinkedCitation() {
        PaperDocument doc = new PaperDocument("test.pdf", 10);

        // Add reference with id=1 only
        List<Zone> refZones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5, "[1] Author A. \"Some Title.\" Journal, 2020.")
        );
        ReferenceParser.parse(refZones, doc);

        // Body text citing [99] which does not exist
        List<Zone> bodyZones = List.of(
            createTextZone(ZoneType.BODY_TEXT, 2, "As discussed in [99], this is notable.")
        );
        CitationLinker.link(bodyZones, doc);

        Assertions.assertEquals(0, doc.getReferences().get(0).getCitationsInText().size());
        Assertions.assertEquals(1, doc.getUnlinkedCitations().size());
    }

    @Test
    public void testAuthorYearCitationLinking() {
        PaperDocument doc = new PaperDocument("test.pdf", 10);

        // Add a reference with year=2024
        List<Zone> refZones = List.of(
            createTextZone(ZoneType.REFERENCE_BODY, 5, "[1] Kim, Lee, \"Paper Title,\" Journal of AI, 2024.")
        );
        ReferenceParser.parse(refZones, doc);
        Assertions.assertEquals(Integer.valueOf(2024), doc.getReferences().get(0).getYear());

        // Body text with author-year citation
        List<Zone> bodyZones = List.of(
            createTextZone(ZoneType.BODY_TEXT, 3, "Recent advances (Kim, 2024) have shown promise.")
        );
        CitationLinker.link(bodyZones, doc);

        Assertions.assertEquals(1, doc.getReferences().get(0).getCitationsInText().size());
    }
}
