package se233.vector_magic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se233.vector_magic.model.ColorMode;
import se233.vector_magic.model.ConversionSettings;
import se233.vector_magic.model.DetailLevel;
import se233.vector_magic.service.ColorDetector;
import se233.vector_magic.service.SvgViewRenderer;
import se233.vector_magic.service.VectorizationService;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VectorizationQualityTest {

    private BufferedImage createDetailedTestImage() {
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 120);

        // Dark Blue sharp rectangle
        g.setColor(new Color(10, 40, 140));
        g.fillRect(15, 15, 90, 40);

        // Gold text/sharp star
        g.setColor(new Color(255, 200, 20));
        g.fillPolygon(new int[]{60, 68, 90, 72, 78, 60, 42, 48, 30, 52},
                      new int[]{20, 32, 32, 42, 58, 48, 58, 42, 32, 32}, 10);

        // Red thin fine circle
        g.setColor(new Color(220, 30, 30));
        g.drawOval(25, 70, 70, 35);
        g.fillOval(55, 80, 10, 10);

        g.dispose();
        return img;
    }

    @Test
    @DisplayName("Quality Test: Dominant color extraction captures visually distinct colors")
    void testDominantColorExtractionDiversity() {
        BufferedImage img = createDetailedTestImage();
        List<Color> colors = ColorDetector.extractDominantColors(img, 3);

        assertEquals(3, colors.size(), "Should extract exactly 3 dominant colors");
        // Ensure distance between any pair is significant (distinct visual colors)
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                Color c1 = colors.get(i);
                Color c2 = colors.get(j);
                double dist = Math.sqrt(
                        Math.pow(c1.getRed() - c2.getRed(), 2) +
                        Math.pow(c1.getGreen() - c2.getGreen(), 2) +
                        Math.pow(c1.getBlue() - c2.getBlue(), 2)
                );
                assertTrue(dist >= 30, "Selected dominant colors must be visually distinct, dist: " + dist);
            }
        }
    }

    @Test
    @DisplayName("Quality Test: Detail levels produce distinctly different path geometries")
    void testDetailLevelGeometries() throws Exception {
        BufferedImage img = createDetailedTestImage();

        String svgHigh = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.HIGH, ColorMode.UNLIMITED, 2, false));
        String svgMed = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.MEDIUM, ColorMode.UNLIMITED, 2, false));
        String svgLow = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.LOW, ColorMode.UNLIMITED, 2, false));

        assertNotNull(svgHigh);
        assertNotNull(svgMed);
        assertNotNull(svgLow);

        // High detail preserves more intricate path data and points
        assertNotEquals(svgHigh, svgMed);
        assertNotEquals(svgMed, svgLow);
        assertNotEquals(svgHigh, svgLow);

        // Verify stroke-linejoin is present for smooth clean joins
        assertTrue(svgHigh.contains("stroke-linejoin=\"round\""));
    }

    @Test
    @DisplayName("Quality Test: SvgViewRenderer parses generated SVG cleanly into JavaFX Group")
    void testSvgRendering() throws Exception {
        BufferedImage img = createDetailedTestImage();
        String svg = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.HIGH, ColorMode.CUSTOM, 3, false));

        assertNotNull(svg);
        javafx.scene.Group group = SvgViewRenderer.renderSvgToGroup(svg);
        assertNotNull(group);
        assertFalse(group.getChildren().isEmpty(), "JavaFX Group must contain parsed vector paths");
    }
}
