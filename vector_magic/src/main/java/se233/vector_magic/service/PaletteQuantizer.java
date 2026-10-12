package se233.vector_magic.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Intelligent Perceptual Color Quantizer.
 * Extracts dominant distinct color palettes and nuanced gradient steps
 * while strictly preventing over-clustering, duplicate color layers,
 * and background color starvation.
 */
public class PaletteQuantizer {

    /**
     * Quantizes an image into an optimal palette of up to targetColors
     * using perceptual Euclidean color distance separation.
     */
    public static List<Color> quantize(BufferedImage image, int targetColors) {
        if (image == null || targetColors <= 0) return Collections.emptyList();

        int w = image.getWidth();
        int h = image.getHeight();
        Map<Integer, Integer> hist = new HashMap<>();

        // Fast adaptive sampling (step 1 for small images, step 2 for large images)
        int step = Math.max(1, (int) Math.sqrt((w * h) / 90000.0));

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = image.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                if (a < 32) continue; // Skip transparent pixels

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 6-bit bins (64 levels per channel)
                int qr = (r >> 2) << 2;
                int qg = (g >> 2) << 2;
                int qb = (b >> 2) << 2;
                int key = (qr << 16) | (qg << 8) | qb;
                hist.put(key, hist.getOrDefault(key, 0) + 1);
            }
        }

        if (hist.isEmpty()) {
            return Collections.singletonList(Color.WHITE);
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(hist.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Color> result = new ArrayList<>();
        // Pure white background is always reserved as base
        result.add(Color.WHITE);

        // Calculate dynamic minimum distance based on requested targetColors
        double minDistance = Math.max(14.0, 160.0 / Math.pow(targetColors, 0.45));

        // Pass 1: Extract dominant colors that satisfy minimum perceptual distance
        for (Map.Entry<Integer, Integer> entry : sorted) {
            if (result.size() >= targetColors) break;
            int key = entry.getKey();
            int r = (key >> 16) & 0xFF;
            int g = (key >> 8) & 0xFF;
            int b = key & 0xFF;
            Color candidate = new Color(r, g, b);

            boolean tooClose = false;
            for (Color existing : result) {
                double dr = candidate.getRed() - existing.getRed();
                double dg = candidate.getGreen() - existing.getGreen();
                double db = candidate.getBlue() - existing.getBlue();
                if (Math.sqrt(dr * dr + dg * dg + db * db) < minDistance) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                result.add(candidate);
            }
        }

        // Pass 2: If more palette slots remain, relax distance slightly
        if (result.size() < targetColors) {
            double relaxedDist = Math.max(10.0, minDistance * 0.7);
            for (Map.Entry<Integer, Integer> entry : sorted) {
                if (result.size() >= targetColors) break;
                int key = entry.getKey();
                int r = (key >> 16) & 0xFF;
                int g = (key >> 8) & 0xFF;
                int b = key & 0xFF;
                Color candidate = new Color(r, g, b);

                boolean tooClose = false;
                for (Color existing : result) {
                    double dr = candidate.getRed() - existing.getRed();
                    double dg = candidate.getGreen() - existing.getGreen();
                    double db = candidate.getBlue() - existing.getBlue();
                    if (Math.sqrt(dr * dr + dg * dg + db * db) < relaxedDist) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    result.add(candidate);
                }
            }
        }

        return result;
    }

    /**
     * Converts a List of Color into ImageTracer's signed byte[][] palette format.
     */
    public static byte[][] toTracerPalette(List<Color> colors) {
        if (colors == null || colors.isEmpty()) return null;
        byte[][] palette = new byte[colors.size()][4];
        for (int i = 0; i < colors.size(); i++) {
            Color c = colors.get(i);
            palette[i][0] = (byte) (c.getRed() - 128);
            palette[i][1] = (byte) (c.getGreen() - 128);
            palette[i][2] = (byte) (c.getBlue() - 128);
            palette[i][3] = (byte) (c.getAlpha() - 128);
        }
        return palette;
    }
}