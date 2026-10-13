package se233.vertor_magic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se233.vertor_magic.model.ColorMode;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.DetailLevel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Conditional Parallel Processing requirements:
 * Both conditions must be satisfied:
 * 1. Detail level is Medium.
 * 2. Color mode is Custom, with color limit up to 5 and default 2.
 */
public class ConditionalParallelTest {

    @Test
    @DisplayName("Medium + Custom (2 colors) should trigger parallel processing")
    void testMediumCustom2Colors() {
        ConversionSettings settings = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 2, false);
        assertTrue(settings.isParallelEligible(), "Medium + Custom (2 colors) must trigger parallel processing");
    }

    @Test
    @DisplayName("Medium + Custom (5 colors) should trigger parallel processing")
    void testMediumCustom5Colors() {
        ConversionSettings settings = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 5, false);
        assertTrue(settings.isParallelEligible(), "Medium + Custom (5 colors) must trigger parallel processing");
    }

    @Test
    @DisplayName("Low + Custom should NOT trigger parallel processing")
    void testLowCustom() {
        ConversionSettings settings = new ConversionSettings(DetailLevel.LOW, ColorMode.CUSTOM, 2, false);
        assertFalse(settings.isParallelEligible(), "Low + Custom must NOT trigger parallel processing");
    }

    @Test
    @DisplayName("High + Custom should NOT trigger parallel processing")
    void testHighCustom() {
        ConversionSettings settings = new ConversionSettings(DetailLevel.HIGH, ColorMode.CUSTOM, 2, false);
        assertFalse(settings.isParallelEligible(), "High + Custom must NOT trigger parallel processing");
    }

    @Test
    @DisplayName("Medium + Unlimited should NOT trigger parallel processing")
    void testMediumUnlimited() {
        ConversionSettings settings = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.UNLIMITED, 2, false);
        assertFalse(settings.isParallelEligible(), "Medium + Unlimited must NOT trigger parallel processing");
    }

    @Test
    @DisplayName("Default settings must be Medium + Custom (2 colors)")
    void testDefaultSettings() {
        ConversionSettings settings = new ConversionSettings();
        assertEquals(DetailLevel.MEDIUM, settings.getDetailLevel());
        assertEquals(ColorMode.CUSTOM, settings.getColorMode());
        assertEquals(2, settings.getCustomColorCount());
        assertFalse(settings.isRemoveBackground());
        assertTrue(settings.isParallelEligible());
    }
}
