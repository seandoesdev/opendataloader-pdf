package org.opendataloader.pdf.paper;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

public class Zone {

    private final int pageNumber;
    private final BoundingBox bounds;
    private final List<IObject> contents;
    private final ZoneFeatures features;
    private ZoneType type;
    private double confidence;
    private String textOverride;

    public Zone(int pageNumber, BoundingBox bounds, List<IObject> contents, ZoneFeatures features) {
        this.pageNumber = pageNumber;
        this.bounds = bounds;
        this.contents = contents;
        this.features = features;
        this.type = ZoneType.UNKNOWN;
        this.confidence = 0.0;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public BoundingBox getBounds() {
        return bounds;
    }

    public List<IObject> getContents() {
        return contents;
    }

    public ZoneFeatures getFeatures() {
        return features;
    }

    public ZoneType getType() {
        return type;
    }

    public void setType(ZoneType type) {
        this.type = type;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public void setTextOverride(String textOverride) {
        this.textOverride = textOverride;
    }

    public String getTextContent() {
        if (textOverride != null) {
            return textOverride;
        }
        StringBuilder sb = new StringBuilder();
        for (IObject obj : contents) {
            if (obj instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) obj;
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(textNode.getValue());
            }
        }
        return sb.toString();
    }
}
