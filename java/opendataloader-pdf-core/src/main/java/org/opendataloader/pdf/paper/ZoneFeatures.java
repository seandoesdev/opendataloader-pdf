package org.opendataloader.pdf.paper;

public class ZoneFeatures {

    private final double relativeY;
    private final double avgFontSize;
    private final double maxFontSize;
    private final double fontSizeRatio;
    private final boolean bold;
    private final boolean centered;
    private final boolean italic;
    private final double textDensity;
    private final int lineCount;
    private final double avgLineLength;
    private final boolean hasNumberPrefix;
    private final boolean hasEmailPattern;
    private final boolean hasDoiPattern;
    private final boolean hasSuperscript;
    private final int pageIndex;
    private final double pageRatio;
    private final boolean isHeading;

    public ZoneFeatures(double relativeY, double avgFontSize, double maxFontSize,
                        double fontSizeRatio, boolean bold, boolean centered,
                        boolean italic, double textDensity, int lineCount,
                        double avgLineLength, boolean hasNumberPrefix,
                        boolean hasEmailPattern, boolean hasDoiPattern,
                        boolean hasSuperscript, int pageIndex, double pageRatio,
                        boolean isHeading) {
        this.relativeY = relativeY;
        this.avgFontSize = avgFontSize;
        this.maxFontSize = maxFontSize;
        this.fontSizeRatio = fontSizeRatio;
        this.bold = bold;
        this.centered = centered;
        this.italic = italic;
        this.textDensity = textDensity;
        this.lineCount = lineCount;
        this.avgLineLength = avgLineLength;
        this.hasNumberPrefix = hasNumberPrefix;
        this.hasEmailPattern = hasEmailPattern;
        this.hasDoiPattern = hasDoiPattern;
        this.hasSuperscript = hasSuperscript;
        this.pageIndex = pageIndex;
        this.pageRatio = pageRatio;
        this.isHeading = isHeading;
    }

    public double getRelativeY() {
        return relativeY;
    }

    public double getAvgFontSize() {
        return avgFontSize;
    }

    public double getMaxFontSize() {
        return maxFontSize;
    }

    public double getFontSizeRatio() {
        return fontSizeRatio;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isCentered() {
        return centered;
    }

    public boolean isItalic() {
        return italic;
    }

    public double getTextDensity() {
        return textDensity;
    }

    public int getLineCount() {
        return lineCount;
    }

    public double getAvgLineLength() {
        return avgLineLength;
    }

    public boolean isHasNumberPrefix() {
        return hasNumberPrefix;
    }

    public boolean isHasEmailPattern() {
        return hasEmailPattern;
    }

    public boolean isHasDoiPattern() {
        return hasDoiPattern;
    }

    public boolean isHasSuperscript() {
        return hasSuperscript;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public double getPageRatio() {
        return pageRatio;
    }

    public boolean isHeading() {
        return isHeading;
    }
}
