package se233.vertor_magic.model;

/**
 * Color modes for vectorization.
 * UNLIMITED: Preserves full original palette.
 * CUSTOM: Restricts colors to user-specified count (2 to 5, default 2).
 */
public enum ColorMode {
    UNLIMITED("Unlimited"),
    CUSTOM("Custom");

    private final String displayName;

    ColorMode(String displayName) {
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
