/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.paper.Zone;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class ZoneDetectorTest {

    @Test
    public void testSplitsByVerticalGap() {
        List<IObject> contents = new ArrayList<>();

        // Paragraph 1 at top of page (topY=700, bottomY=680)
        SemanticParagraph para1 = createParagraph(10.0, 680.0, 200.0, 700.0, "First paragraph", 12.0);
        contents.add(para1);

        // Paragraph 2 far below (topY=500, bottomY=480) - gap of 180 > 12*2=24
        SemanticParagraph para2 = createParagraph(10.0, 480.0, 200.0, 500.0, "Second paragraph", 12.0);
        contents.add(para2);

        List<Zone> zones = ZoneDetector.detect(contents, 0, 10, 12.0);

        Assertions.assertEquals(2, zones.size());
    }

    @Test
    public void testSplitsByFontSizeChange() {
        List<IObject> contents = new ArrayList<>();

        // Paragraph 1 with font size 20 (topY=700, bottomY=680)
        SemanticParagraph para1 = createParagraph(10.0, 680.0, 200.0, 700.0, "Large text", 20.0);
        contents.add(para1);

        // Paragraph 2 with font size 10 (topY=670, bottomY=650) - close vertically but different font
        SemanticParagraph para2 = createParagraph(10.0, 650.0, 200.0, 670.0, "Small text", 10.0);
        contents.add(para2);

        List<Zone> zones = ZoneDetector.detect(contents, 0, 10, 12.0);

        Assertions.assertEquals(2, zones.size());
    }

    @Test
    public void testHeadingForcesNewZone() {
        List<IObject> contents = new ArrayList<>();

        // Paragraph (topY=700, bottomY=680)
        SemanticParagraph para1 = createParagraph(10.0, 680.0, 200.0, 700.0, "Introduction text", 12.0);
        contents.add(para1);

        // Heading (topY=670, bottomY=655) - close to previous, but heading forces split
        SemanticHeading heading = createHeading(10.0, 655.0, 200.0, 670.0, "Section Title", 14.0);
        contents.add(heading);

        // Paragraph after heading (topY=645, bottomY=625)
        SemanticParagraph para2 = createParagraph(10.0, 625.0, 200.0, 645.0, "Body text", 12.0);
        contents.add(para2);

        List<Zone> zones = ZoneDetector.detect(contents, 0, 10, 12.0);

        Assertions.assertTrue(zones.size() >= 2, "Heading should force at least 2 zones, got " + zones.size());
    }

    @Test
    public void testEmptyContentsReturnsEmptyList() {
        List<Zone> zones = ZoneDetector.detect(new ArrayList<>(), 0, 10, 12.0);
        Assertions.assertTrue(zones.isEmpty());

        List<Zone> zonesNull = ZoneDetector.detect(null, 0, 10, 12.0);
        Assertions.assertTrue(zonesNull.isEmpty());
    }

    // Helper methods

    private SemanticParagraph createParagraph(double leftX, double bottomY, double rightX, double topY,
                                               String text, double fontSize) {
        SemanticParagraph paragraph = new SemanticParagraph();
        TextChunk chunk = new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY),
                text, "Font1", fontSize, 400, 0, bottomY, new double[]{0.0}, null, 0);
        TextLine line = new TextLine(chunk);
        paragraph.getColumns().add(new TextColumn());
        paragraph.getLastColumn().getBlocks().add(new org.verapdf.wcag.algorithms.entities.content.TextBlock(line));
        paragraph.setBoundingBox(new BoundingBox(0, leftX, bottomY, rightX, topY));
        paragraph.setCorrectSemanticScore(1.0);
        return paragraph;
    }

    private SemanticHeading createHeading(double leftX, double bottomY, double rightX, double topY,
                                           String text, double fontSize) {
        SemanticHeading heading = new SemanticHeading();
        TextChunk chunk = new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY),
                text, "Font1", fontSize, 700, 0, bottomY, new double[]{0.0}, null, 0);
        TextLine line = new TextLine(chunk);
        heading.add(line);
        heading.setBoundingBox(new BoundingBox(0, leftX, bottomY, rightX, topY));
        return heading;
    }
}
