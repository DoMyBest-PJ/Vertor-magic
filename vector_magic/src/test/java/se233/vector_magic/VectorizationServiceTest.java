package se233.vector_magic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se233.vector_magic.model.ColorMode;
import se233.vector_magic.model.ConversionSettings;
import se233.vector_magic.model.DetailLevel;
import se233.vector_magic.service.ColorDetector;
import se233.vector_magic.service.VectorizationService;
import se233.vector_magic.service.ZipService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VectorizationServiceTest {

    private BufferedImage createSampleImage() {
        BufferedImage img = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 60, 60);
        // Red circle
        g.setColor(Color.RED);
        g.fillOval(10, 10, 40, 40);
        // Blue square inside
        g.setColor(Color.BLUE);
        g.fillRect(20, 20, 20, 20);
        g.dispose();
        return img;
    }

    @Test
    @DisplayName("Vectorize image produces valid SVG with svg tags and path elements")
    void testVectorizeValidSVG() throws Exception {
        BufferedImage sample = createSampleImage();
        ConversionSettings settings = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 3, false);

        String svg = VectorizationService.vectorize(sample, settings);

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg"), "Output must start with <svg");
        assertTrue(svg.endsWith("</svg>"), "Output must end with </svg>");
        assertTrue(svg.contains("<path"), "Output must contain vector <path> elements");
    }

    @Test
    @DisplayName("Detail levels (Low, Medium, High) affect output")
    void testDetailLevels() throws Exception {
        BufferedImage sample = createSampleImage();

        String svgLow = VectorizationService.vectorize(sample, new ConversionSettings(DetailLevel.LOW, ColorMode.CUSTOM, 2, false));
        String svgMed = VectorizationService.vectorize(sample, new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 2, false));
        String svgHigh = VectorizationService.vectorize(sample, new ConversionSettings(DetailLevel.HIGH, ColorMode.CUSTOM, 2, false));

        assertNotNull(svgLow);
        assertNotNull(svgMed);
        assertNotNull(svgHigh);
        assertTrue(svgLow.contains("<path"));
        assertTrue(svgMed.contains("<path"));
        assertTrue(svgHigh.contains("<path"));
        assertNotEquals(svgLow, svgHigh, "Low and High detail must produce distinct vector paths");
        assertNotEquals(svgLow, svgMed, "Low and Medium detail must produce distinct vector paths");
    }

    @Test
    @DisplayName("Color detection correctly detects multiple colors and caps Custom mode at 5")
    void testColorDetectionAndCapping() {
        BufferedImage sample = createSampleImage();
        int uniqueColors = ColorDetector.countUniqueColors(sample);

        assertTrue(uniqueColors >= 3, "Sample image has at least 3 distinct colors");

        int cappedLimit = ColorDetector.getCustomColorUpperLimit(uniqueColors);
        assertTrue(cappedLimit <= 5, "Custom color limit must be capped at 5");
        assertTrue(cappedLimit >= 2, "Custom color limit must be at least 2");
    }

    @Test
    @DisplayName("Remove background produces SVG without background layer")
    void testRemoveBackground() throws Exception {
        BufferedImage sample = createSampleImage();

        ConversionSettings withBg = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 3, false);
        ConversionSettings withoutBg = new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, 3, true);

        String svgWith = VectorizationService.vectorize(sample, withBg);
        String svgWithout = VectorizationService.vectorize(sample, withoutBg);

        assertNotNull(svgWith);
        assertNotNull(svgWithout);
        assertNotEquals(svgWith, svgWithout, "Removing background must alter the SVG output");
    }

    @Test
    @DisplayName("ZipService extracts JPG and PNG images from ZIP file")
    void testZipExtraction() throws IOException {
        File tempZip = File.createTempFile("test_images", ".zip");
        tempZip.deleteOnExit();

        BufferedImage img = createSampleImage();
        File tempPng = File.createTempFile("test_sample", ".png");
        tempPng.deleteOnExit();
        ImageIO.write(img, "png", tempPng);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            ZipEntry entry = new ZipEntry("nested/sample.png");
            zos.putNextEntry(entry);
            Files.copy(tempPng.toPath(), zos);
            zos.closeEntry();
        }

        List<File> extracted = ZipService.extractImages(tempZip);
        assertFalse(extracted.isEmpty(), "Extracted list should not be empty");
        assertEquals("sample.png", extracted.get(0).getName());
        assertTrue(extracted.get(0).exists());
    }
}
