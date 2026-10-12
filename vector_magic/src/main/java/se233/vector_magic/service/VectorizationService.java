package se233.vector_magic.service;

import se233.vector_magic.ImageTracer;
import se233.vector_magic.model.ColorMode;
import se233.vector_magic.model.ConversionSettings;
import se233.vector_magic.model.DetailLevel;
import se233.vector_magic.model.ImageItem;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VectorizationService {

    public static String vectorize(BufferedImage image, ConversionSettings settings) throws Exception {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        if (settings == null) {
            settings = new ConversionSettings();
        }

        HashMap<String, Float> options = ImageTracer.checkoptions(null);

        options.put("viewbox", 1f);
        options.put("scale", 1f);
        options.put("roundcoords", 1f);

        applyDetailLevelSettings(options, settings.getDetailLevel());

        byte[][] palette = preparePalette(image, options, settings);

        ImageTracer.ImageData imgd = ImageTracer.loadImageData(image);
        ImageTracer.IndexedImage ii = ImageTracer.imagedataToTracedata(imgd, options, palette);

        int bgLayerIdx = detectBackgroundLayerIndex(ii);

        return ImageTracer.getsvgstring(ii, options, settings.isRemoveBackground(), bgLayerIdx);
    }

    public static void processImageItem(ImageItem item) throws Exception {
        if (item == null || item.getBufferedImage() == null) return;

        if (item.getDetectedColorCount() == 0) {
            int detectedColors = ColorDetector.countUniqueColors(item.getBufferedImage());
            item.setDetectedColorCount(detectedColors);
            item.setDetectedPalette(ColorDetector.extractDominantColors(item.getBufferedImage(), 5));
        }

        String svg = vectorize(item.getBufferedImage(), item.getSettings());
        item.setSvgContent(svg);
        item.setProcessed(true);
        item.setProcessingError(null);
    }

    private static void applyDetailLevelSettings(HashMap<String, Float> options, DetailLevel detailLevel) {
        if (detailLevel == null) detailLevel = DetailLevel.MEDIUM;

        switch (detailLevel) {
            case LOW:
                // Minimalist / Flat logo style: bold simplified geometry, robust noise rejection
                options.put("ltres", 2.0f);
                options.put("qtres", 1.6f);
                options.put("pathomit", 8.0f);
                options.put("strokewidth", 0.4f);
                break;
            case HIGH:
                // High fidelity: razor-sharp edges, intact fine typography, nuanced gradation
                options.put("ltres", 0.8f);
                options.put("qtres", 0.6f);
                options.put("pathomit", 4.0f);
                options.put("strokewidth", 0.3f);
                break;
            case MEDIUM:
            default:
                // Balanced vector illustration: clean curved contours, smooth posterized tonal steps
                options.put("ltres", 1.2f);
                options.put("qtres", 1.0f);
                options.put("pathomit", 6.0f);
                options.put("strokewidth", 0.35f);
                break;
        }
    }

    private static byte[][] preparePalette(BufferedImage image, HashMap<String, Float> options, ConversionSettings settings) {
        if (settings.getColorMode() == ColorMode.CUSTOM) {
            int numColors = settings.getCustomColorCount();
            numColors = Math.max(2, Math.min(5, numColors));

            options.put("numberofcolors", (float) numColors);
            options.put("colorsampling", 1f);
            options.put("colorquantcycles", 1f);
            options.put("mincolorratio", 0f);

            List<Color> paletteColors = ColorDetector.extractDominantColors(image, numColors);
            return PaletteQuantizer.toTracerPalette(paletteColors);
        } else {
            DetailLevel detail = settings.getDetailLevel();
            int numColors;
            switch (detail) {
                case HIGH:
                    numColors = 40;
                    break;
                case LOW:
                    numColors = 10;
                    break;
                case MEDIUM:
                default:
                    numColors = 20;
                    break;
            }

            options.put("numberofcolors", (float) numColors);
            options.put("colorsampling", 1f);
            options.put("colorquantcycles", 3f);
            options.put("mincolorratio", 0.005f);

            // In unlimited mode, null palette lets ImageTracer dynamically cluster full image colors
            return null;
        }
    }

    private static int detectBackgroundLayerIndex(ImageTracer.IndexedImage ii) {
        if (ii == null || ii.array == null || ii.array.length == 0 || ii.palette == null) {
            return -1;
        }

        int h = ii.height;
        int w = ii.width;
        Map<Integer, Integer> cornerCounts = new HashMap<>();

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

        for (int x = 1; x <= w; x += Math.max(1, w / 16)) {
            int topIdx = ii.array[1][x];
            int botIdx = ii.array[h][x];
            cornerCounts.put(topIdx, cornerCounts.getOrDefault(topIdx, 0) + 1);
            cornerCounts.put(botIdx, cornerCounts.getOrDefault(botIdx, 0) + 1);
        }

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