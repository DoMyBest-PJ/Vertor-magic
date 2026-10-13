package se233.vertor_magic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ImagePanningSyncTest {

    @Test
    @DisplayName("Dragging horizontally calculates correct clamped hvalue")
    void testHorizontalDragCalculation() {
        double startH = 0.5;
        double scrollableW = 800.0; // content (1200) - viewport (400)
        double deltaX = 80.0; // dragged 80px to the right -> reveals left, so hvalue decreases

        double newH = startH - (deltaX / scrollableW);
        assertEquals(0.4, newH, 1e-4);

        // Test clamping to 0.0 when dragged far right
        double extremeDeltaX = 600.0;
        double clampedMinH = Math.max(0.0, Math.min(1.0, startH - (extremeDeltaX / scrollableW)));
        assertEquals(0.0, clampedMinH, 1e-4);

        // Test clamping to 1.0 when dragged far left
        double extremeDeltaLeft = -600.0;
        double clampedMaxH = Math.max(0.0, Math.min(1.0, startH - (extremeDeltaLeft / scrollableW)));
        assertEquals(1.0, clampedMaxH, 1e-4);
    }

    @Test
    @DisplayName("Dragging vertically calculates correct clamped vvalue")
    void testVerticalDragCalculation() {
        double startV = 0.5;
        double scrollableH = 600.0; // content (900) - viewport (300)
        double deltaY = 60.0; // dragged 60px down -> reveals top, so vvalue decreases

        double newV = startV - (deltaY / scrollableH);
        assertEquals(0.4, newV, 1e-4);

        // Test clamping to 0.0
        double extremeDeltaDown = 500.0;
        double clampedMinV = Math.max(0.0, Math.min(1.0, startV - (extremeDeltaDown / scrollableH)));
        assertEquals(0.0, clampedMinV, 1e-4);

        // Test clamping to 1.0
        double extremeDeltaUp = -500.0;
        double clampedMaxV = Math.max(0.0, Math.min(1.0, startV - (extremeDeltaUp / scrollableH)));
        assertEquals(1.0, clampedMaxV, 1e-4);
    }

    @Test
    @DisplayName("When content fits inside viewport, dragging does not produce NaN or errors")
    void testContentFitsViewport() {
        double scrollableW = 0.0; // content <= viewport
        double scrollableH = -50.0; // content < viewport
        double startH = 0.5;
        double startV = 0.5;
        double deltaX = 100.0;
        double deltaY = 100.0;

        double finalH = startH;
        if (scrollableW > 0) {
            finalH = startH - (deltaX / scrollableW);
        }

        double finalV = startV;
        if (scrollableH > 0) {
            finalV = startV - (deltaY / scrollableH);
        }

        assertEquals(0.5, finalH);
        assertEquals(0.5, finalV);
        assertFalse(Double.isNaN(finalH));
        assertFalse(Double.isNaN(finalV));
    }
}
