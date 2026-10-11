package se233.vertor_magic.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Service for detecting image colors, counting unique colors,
 * extracting dominant palettes, and identifying background colors.
 */
public class ColorDetector {

    /**
     * Counts the number of unique colors in the image (or samples if large image).
     */
    public static int countUniqueColors(BufferedImage image) {
        if (image == null) return 0;
        int width = image.getWidth();
        int height = image.getHeight();

        Set<Integer> uniqueColors = new HashSet<>();
        // Sample up to 50,000 pixels for fast and responsive detection
        int step = Math.max(1, (int) Math.sqrt((width * height) / 50000.0));

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                // Mask out alpha to count visual RGB colors
                uniqueColors.add(rgb & 0x00FFFFFF);
                if (uniqueColors.size() > 500) {
                    // Clearly more than 5 colors, safe to return early
                    return uniqueColors.size();
                }
            }
        }
        return uniqueColors.size();
    }

    /**
     * In Custom mode (Task 5):
     * If the image contains more than 5 colors, the custom selection limit must be capped at 5.
     * If it contains fewer than 5 (e.g. 3), the max limit is that count (minimum 2).
     */
    public static int getCustomColorUpperLimit(int detectedColorCount) {
        if (detectedColorCount >= 5) {
            return 5;
        }
        return Math.max(2, detectedColorCount);
    }

    /**
     * Detects the dominant background color by sampling the 4 corners and borders.
     */
    public static Color detectBackgroundColor(BufferedImage image) {
        if (image == null) return Color.WHITE;
        int width = image.getWidth();
        int height = image.getHeight();

        Map<Integer, Integer> borderFreq = new HashMap<>();

        // Sample corners and borders
        int[] sampleX = {0, width - 1, width / 2};
        int[] sampleY = {0, height - 1, height / 2};

        for (int x : sampleX) {
            for (int y : sampleY) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    int rgb = image.getRGB(x, y);
                    borderFreq.put(rgb, borderFreq.getOrDefault(rgb, 0) + 1);
                }
            }
        }

        // Also sample top and bottom rows
        for (int x = 0; x < width; x += Math.max(1, width / 10)) {
            int topRgb = image.getRGB(x, 0);
            int botRgb = image.getRGB(x, height - 1);
            borderFreq.put(topRgb, borderFreq.getOrDefault(topRgb, 0) + 1);
            borderFreq.put(botRgb, borderFreq.getOrDefault(botRgb, 0) + 1);
        }

        int dominantRgb = 0xFFFFFFFF; // default white
        int maxCount = -1;
        for (Map.Entry<Integer, Integer> entry : borderFreq.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantRgb = entry.getKey();
            }
        }

        return new Color(dominantRgb, true);
    }

    /**
     * Extracts dominant colors from the image up to maxColors using frequency analysis
     * combined with color-distance diversity selection to ensure distinct visual hues.
     */
    public static List<Color> extractDominantColors(BufferedImage image, int maxColors) {
        List<Color> result = new ArrayList<>();
        if (image == null || maxColors <= 0) return result;

        int width = image.getWidth();
        int height = image.getHeight();
        Map<Integer, Integer> colorCounts = new HashMap<>();
        int step = Math.max(1, (int) Math.sqrt((width * height) / 30000.0));

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                if (a < 32) continue; // Skip near-transparent pixels

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Quantize to 5 bits per channel (32 levels per channel)
                int qr = (r >> 3) << 3;
                int qg = (g >> 3) << 3;
                int qb = (b >> 3) << 3;
                int quantized = (qr << 16) | (qg << 8) | qb;
                colorCounts.put(quantized, colorCounts.getOrDefault(quantized, 0) + 1);
            }
        }

        if (colorCounts.isEmpty()) {
            result.add(Color.BLACK);
            return result;
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(colorCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Color> candidates = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : sorted) {
            int rgb = entry.getKey();
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            candidates.add(new Color(r, g, b));
        }

        // Color-distance diversity selection:
        // Try decreasing Euclidean distance thresholds in RGB to select truly distinct colors
        // rather than slight compression noise of the dominant background.
        int[] distanceThresholds = {80, 50, 30, 15, 0};
        for (int distThreshold : distanceThresholds) {
            result.clear();
            for (Color candidate : candidates) {
                boolean distinct = true;
                for (Color selected : result) {
                    int dr = candidate.getRed() - selected.getRed();
                    int dg = candidate.getGreen() - selected.getGreen();
                    int db = candidate.getBlue() - selected.getBlue();
                    double dist = Math.sqrt(dr * dr + dg * dg + db * db);
                    if (dist < distThreshold) {
                        distinct = false;
                        break;
                    }
                }
                if (distinct) {
                    result.add(candidate);
                    if (result.size() == maxColors) break;
                }
            }
            if (result.size() >= maxColors) {
                break;
            }
        }

        return result;
    }
}
