package se233.vertor_magic.model;

import java.util.Objects;

/**
 * Encapsulates settings for raster-to-vector conversion.
 */
public class ConversionSettings {
    private DetailLevel detailLevel;
    private ColorMode colorMode;
    private int customColorCount;
    private boolean removeBackground;

    public ConversionSettings() {
        // Defaults per Task 7 and Task 9:
        // Preprocessing and default state: Medium detail and 2 custom colors
        this.detailLevel = DetailLevel.MEDIUM;
        this.colorMode = ColorMode.CUSTOM;
        this.customColorCount = 2;
        this.removeBackground = false;
    }

    public ConversionSettings(DetailLevel detailLevel, ColorMode colorMode, int customColorCount, boolean removeBackground) {
        this.detailLevel = detailLevel != null ? detailLevel : DetailLevel.MEDIUM;
        this.colorMode = colorMode != null ? colorMode : ColorMode.CUSTOM;
        this.customColorCount = Math.max(2, Math.min(5, customColorCount));
        this.removeBackground = removeBackground;
    }

    public ConversionSettings(ConversionSettings other) {
        if (other != null) {
            this.detailLevel = other.detailLevel;
            this.colorMode = other.colorMode;
            this.customColorCount = other.customColorCount;
            this.removeBackground = other.removeBackground;
        } else {
            this.detailLevel = DetailLevel.MEDIUM;
            this.colorMode = ColorMode.CUSTOM;
            this.customColorCount = 2;
            this.removeBackground = false;
        }
    }

    /**
     * Checks if current settings satisfy Task 9 conditional parallel processing:
     * Condition 1: Detail level is Medium.
     * Condition 2: Color mode is Custom, with a color limit of up to 5 and a default of 2 (range 2 to 5).
     */
    public boolean isParallelEligible() {
        return detailLevel == DetailLevel.MEDIUM
                && colorMode == ColorMode.CUSTOM
                && customColorCount >= 2
                && customColorCount <= 5;
    }

    public DetailLevel getDetailLevel() {
        return detailLevel;
    }

    public void setDetailLevel(DetailLevel detailLevel) {
        this.detailLevel = detailLevel;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    public int getCustomColorCount() {
        return customColorCount;
    }

    public void setCustomColorCount(int customColorCount) {
        this.customColorCount = Math.max(2, Math.min(5, customColorCount));
    }

    public boolean isRemoveBackground() {
        return removeBackground;
    }

    public void setRemoveBackground(boolean removeBackground) {
        this.removeBackground = removeBackground;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversionSettings that = (ConversionSettings) o;
        return customColorCount == that.customColorCount &&
                removeBackground == that.removeBackground &&
                detailLevel == that.detailLevel &&
                colorMode == that.colorMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(detailLevel, colorMode, customColorCount, removeBackground);
    }

    @Override
    public String toString() {
        return "ConversionSettings{" +
                "detailLevel=" + detailLevel +
                ", colorMode=" + colorMode +
                ", customColorCount=" + customColorCount +
                ", removeBackground=" + removeBackground +
                ", parallelEligible=" + isParallelEligible() +
                '}';
    }
}
