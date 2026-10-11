package se233.vertor_magic.service;

import se233.vertor_magic.ImageTracer;
import se233.vertor_magic.model.ColorMode;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.DetailLevel;
import se233.vertor_magic.model.ImageItem;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for converting raster images (JPG/PNG) into SVG vector representations.
 * Handles Detail Levels (Low, Medium, High), Color Modes (Unlimited, Custom),
 * and Background Removal (Transparent SVG output).
 */
public class VectorizationService {

    /**
     * Converts a BufferedImage into an SVG string according to the given ConversionSettings.
     *
     * @param image    The source image
     * @param settings The conversion settings
     * @return Generated SVG string
     * @throws Exception If conversion fails
     */
    public static String vectorize(BufferedImage image, ConversionSettings settings) throws Exception {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        if (settings == null) {
            settings = new ConversionSettings();
        }

        HashMap<String, Float> options = new HashMap<>();

        // Always enable viewBox for scalable, responsive preview rendering
        options.put("viewbox", 1f);
        options.put("scale", 1f);
        // Full double precision coordinates without truncation to prevent curve wobble
        options.put("roundcoords", -1f);

        // 1. Configure Detail Level (Task 4)
        applyDetailLevelSettings(options, settings.getDetailLevel());

        // 2. Configure Color Mode & Intelligent Palette Seeding (Task 5)
        byte[][] palette = preparePalette(image, options, settings);

        // 3. Load ImageData and Trace
        ImageTracer.ImageData imgd = ImageTracer.loadImageData(image);
        ImageTracer.IndexedImage ii = ImageTracer.imagedataToTracedata(imgd, options, palette);

        // 4. Determine background layer index for transparency removal (Task 6)
        int bgLayerIdx = detectBackgroundLayerIndex(ii);

        // 5. Generate SVG string (omitting background layer if removeBackground is enabled)
        return ImageTracer.getsvgstring(ii, options, settings.isRemoveBackground(), bgLayerIdx);
    }

    /**
     * Process an ImageItem: detects colors and generates SVG based on item settings.
     */
    public static void processImageItem(ImageItem item) throws Exception {
        if (item == null || item.getBufferedImage() == null) return;

        // Detect colors if not already detected
        if (item.getDetectedColorCount() == 0) {
            int detectedColors = ColorDetector.countUniqueColors(item.getBufferedImage());
            item.setDetectedColorCount(detectedColors);
            item.setDetectedPalette(ColorDetector.extractDominantColors(item.getBufferedImage(), 5));
        }

        // Trace and generate SVG
        String svg = vectorize(item.getBufferedImage(), item.getSettings());
        item.setSvgContent(svg);
        item.setProcessed(true);
        item.setProcessingError(null);
    }

    private static void applyDetailLevelSettings(HashMap<String, Float> options, DetailLevel detailLevel) {
        if (detailLevel == null) detailLevel = DetailLevel.MEDIUM;

        switch (detailLevel) {
            case LOW:
                // Simplified geometry, bold minimalist curves, larger path omission
                options.put("ltres", 1.8f);
                options.put("qtres", 2.0f);
                options.put("pathomit", 12f);
                options.put("strokewidth", 0.3f);
                options.put("blurradius", 0f);
                options.put("blurdelta", 20f);
                break;
            case HIGH:
                // Maximum sharpness, tight contour adherence, preserve 100% fine details
                options.put("ltres", 0.05f);
                options.put("qtres", 0.1f);
                options.put("pathomit", 0f);
                options.put("strokewidth", 0.1f);
                options.put("blurradius", 0f);
                options.put("blurdelta", 20f);
                break;
            case MEDIUM:
            default:
                // Balanced fidelity and smoothing, clean professional vector curves
                options.put("ltres", 0.5f);
                options.put("qtres", 0.6f);
                options.put("pathomit", 3f);
                options.put("strokewidth", 0.2f);
                options.put("blurradius", 0f);
                options.put("blurdelta", 20f);
                break;
        }
    }

    private static byte[][] preparePalette(BufferedImage image, HashMap<String, Float> options, ConversionSettings settings) {
        if (settings.getColorMode() == ColorMode.CUSTOM) {
            int numColors = settings.getCustomColorCount();
            numColors = Math.max(2, Math.min(5, numColors)); // 2 to 5 per Task 5

            options.put("numberofcolors", (float) numColors);
            options.put("colorsampling", 1f);
            options.put("colorquantcycles", 10f); // 10 cycles for optimal convergence
            options.put("mincolorratio", 0f); // Never discard any of the requested colors

            // Seed K-means with diverse dominant colors from image
            List<Color> dominantColors = ColorDetector.extractDominantColors(image, numColors);
            if (!dominantColors.isEmpty()) {
                byte[][] palette = new byte[dominantColors.size()][4];
                for (int i = 0; i < dominantColors.size(); i++) {
                    Color c = dominantColors.get(i);
                    palette[i][0] = (byte)(c.getRed() - 128);
                    palette[i][1] = (byte)(c.getGreen() - 128);
                    palette[i][2] = (byte)(c.getBlue() - 128);
                    palette[i][3] = (byte)(c.getAlpha() - 128);
                }
                return palette;
            }
            return null;
        } else {
            // Unlimited color mode: scales palette size and refinement by Detail Level
            DetailLevel detail = settings.getDetailLevel();
            float numColors;
            float cycles;
            switch (detail) {
                case HIGH:
                    numColors = 64f;
                    cycles = 10f;
                    break;
                case LOW:
                    numColors = 16f;
                    cycles = 6f;
                    break;
                case MEDIUM:
                default:
                    numColors = 36f;
                    cycles = 8f;
                    break;
            }

            options.put("numberofcolors", numColors);
            options.put("colorsampling", 1f);
            options.put("colorquantcycles", cycles);
            options.put("mincolorratio", 0.001f);
            return null; // Uses deterministic spatial grid sampling in ImageTracer
        }
    }

    /**
     * Finds the palette layer index corresponding to the background
     * by checking the 4 corners and outermost perimeter of the indexed image.
     */
    private static int detectBackgroundLayerIndex(ImageTracer.IndexedImage ii) {
        if (ii == null || ii.array == null || ii.array.length == 0 || ii.palette == null) {
            return -1;
        }

        int h = ii.height;
        int w = ii.width;
        Map<Integer, Integer> cornerCounts = new HashMap<>();

        // Check 4 corners (coordinates in ii.array have 1-based offset from padding)
        int[] cornerY = {1, 1, h, h};
        int[] cornerX = {1, w, 1, w};

        for (int i = 0; i < 4; i++) {
            int y = cornerY[i];
            int x = cornerX[i];
            if (y < ii.array.length && x < ii.array[y].length) {
                int paletteIdx = ii.array[y][x];
                cornerCounts.put(paletteIdx, cornerCounts.getOrDefault(paletteIdx, 0) + 1);
            }
        }

        // Also check perimeter points (top and bottom)
        for (int x = 1; x <= w; x += Math.max(1, w / 16)) {
            int topIdx = ii.array[1][x];
            int botIdx = ii.array[h][x];
            cornerCounts.put(topIdx, cornerCounts.getOrDefault(topIdx, 0) + 1);
            cornerCounts.put(botIdx, cornerCounts.getOrDefault(botIdx, 0) + 1);
        }

        // Also check perimeter points (left and right)
        for (int y = 1; y <= h; y += Math.max(1, h / 16)) {
            int leftIdx = ii.array[y][1];
            int rightIdx = ii.array[y][w];
            cornerCounts.put(leftIdx, cornerCounts.getOrDefault(leftIdx, 0) + 1);
            cornerCounts.put(rightIdx, cornerCounts.getOrDefault(rightIdx, 0) + 1);
        }

        int mostFrequentIdx = -1;
        int maxCount = -1;
        for (Map.Entry<Integer, Integer> entry : cornerCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostFrequentIdx = entry.getKey();
            }
        }

        return mostFrequentIdx;
    }
}
