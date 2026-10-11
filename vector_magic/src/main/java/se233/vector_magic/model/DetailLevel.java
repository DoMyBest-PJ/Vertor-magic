package se233.vector_magic.model;

/**
 * Detail levels for vectorization.
 * LOW: Coarse details, simplified curves, smaller SVG file size.
 * MEDIUM: Balanced detail and smoothness.
 * HIGH: Fine curve details and strict path preservation.
 */
public enum DetailLevel {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String displayName;

    DetailLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
