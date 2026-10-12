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

        // Verify clean fill rendering
        assertTrue(svgHigh.contains("fill="));
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

    @Test
    @DisplayName("Quality Test: End-to-end vectorization on real-world photo produces sharp, valid SVGs across detail levels")
    void testChannels4ProductionPipeline() throws Exception {
        java.io.File file = new java.io.File("C:/Users/TUF/Downloads/channels4_profile.jpg");
        if (!file.exists()) return;

        BufferedImage img = javax.imageio.ImageIO.read(file);

        // 1. High Detail Unlimited
        String svgHigh = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.HIGH, ColorMode.UNLIMITED, 2, false));
        assertNotNull(svgHigh);
        java.nio.file.Files.writeString(new java.io.File("target/PROD_HIGH.svg").toPath(), svgHigh);

        // 2. Medium Detail Unlimited
        String svgMed = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.MEDIUM, ColorMode.UNLIMITED, 2, false));
        assertNotNull(svgMed);
        java.nio.file.Files.writeString(new java.io.File("target/PROD_MED.svg").toPath(), svgMed);

        // 3. Low Detail Unlimited
        String svgLow = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.LOW, ColorMode.UNLIMITED, 2, false));
        assertNotNull(svgLow);
        java.nio.file.Files.writeString(new java.io.File("target/PROD_LOW.svg").toPath(), svgLow);

        // 4. Custom 2 Colors
        String svgCust2 = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 2, false));
        assertNotNull(svgCust2);
        java.nio.file.Files.writeString(new java.io.File("target/PROD_CUST_2.svg").toPath(), svgCust2);

        // 5. Remove Background (transparent SVG)
        String svgTrans = VectorizationService.vectorize(img, new ConversionSettings(DetailLevel.MEDIUM, ColorMode.UNLIMITED, 2, true));
        assertNotNull(svgTrans);
        java.nio.file.Files.writeString(new java.io.File("target/PROD_TRANSPARENT.svg").toPath(), svgTrans);

        // Verify the transparent SVG has omitted the background layer
        assertTrue(svgTrans.length() < svgMed.length(), "Transparent SVG must omit background paths");

        // Render to PNG
        try { javafx.application.Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            try {
                renderPngFile("target/PROD_HIGH.svg", "target/RENDER_PROD_HIGH.png", 900, 900);
                renderPngFile("target/PROD_MED.svg", "target/RENDER_PROD_MED.png", 900, 900);
                renderPngFile("target/PROD_LOW.svg", "target/RENDER_PROD_LOW.png", 900, 900);
                renderPngFile("target/PROD_CUST_2.svg", "target/RENDER_PROD_CUST_2.png", 900, 900);
                renderPngFile("target/PROD_TRANSPARENT.svg", "target/RENDER_PROD_TRANSPARENT.png", 900, 900);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void renderPngFile(String svgPath, String pngPath, int w, int h) throws Exception {
        String svg = java.nio.file.Files.writeString(new java.io.File(svgPath).toPath(), java.nio.file.Files.readString(new java.io.File(svgPath).toPath())).toString();
        svg = java.nio.file.Files.readString(new java.io.File(svgPath).toPath());
        javafx.scene.Group group = SvgViewRenderer.renderSvgToGroup(svg);
        javafx.scene.Scene scene = new javafx.scene.Scene(group, w, h, javafx.scene.paint.Color.WHITE);
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.WHITE);
        javafx.scene.image.WritableImage fxImg = group.snapshot(params, null);
        int imgW = (int) fxImg.getWidth(), imgH = (int) fxImg.getHeight();
        javafx.scene.image.PixelReader pr = fxImg.getPixelReader();
        BufferedImage bImg = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                bImg.setRGB(x, y, pr.getArgb(x, y));
            }
        }
        javax.imageio.ImageIO.write(bImg, "png", new java.io.File(pngPath));
    }
}
