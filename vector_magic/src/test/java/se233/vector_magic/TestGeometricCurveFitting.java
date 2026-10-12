package se233.vector_magic;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import se233.vector_magic.service.ColorDetector;
import se233.vector_magic.service.PaletteQuantizer;
import se233.vector_magic.service.SvgViewRenderer;

public class TestGeometricCurveFitting {

    @Test
    void testFitting() throws Exception {
        BufferedImage original = ImageIO.read(new File("C:/Users/TUF/Downloads/channels4_profile.jpg"));
        
        // Test High Detail: 40 colors, fine geometric tolerances
        String svgHigh = traceWithSettings(original, 40, 0.8f, 0.6f, 4.0f, 0.3f);
        Files.writeString(new File("target/V2_HIGH.svg").toPath(), svgHigh);
        
        // Test Medium Detail: 20 colors, balanced tolerances
        String svgMed = traceWithSettings(original, 20, 1.2f, 1.0f, 6.0f, 0.35f);
        Files.writeString(new File("target/V2_MED.svg").toPath(), svgMed);
        
        // Test Low Detail: 10 colors, simplified geometry
        String svgLow = traceWithSettings(original, 10, 2.0f, 1.6f, 8.0f, 0.4f);
        Files.writeString(new File("target/V2_LOW.svg").toPath(), svgLow);

        // Test Custom Mode: 2 colors
        String svgCustom2 = traceWithCustomColors(original, 2, 1.2f, 1.0f, 6.0f, 0.35f);
        Files.writeString(new File("target/V2_CUSTOM_2.svg").toPath(), svgCustom2);

        // Test Custom Mode: 5 colors
        String svgCustom5 = traceWithCustomColors(original, 5, 1.2f, 1.0f, 6.0f, 0.35f);
        Files.writeString(new File("target/V2_CUSTOM_5.svg").toPath(), svgCustom5);
        
        System.out.println("Generated V2 SVGs!");
        
        // Render to PNG
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                renderPng("target/V2_HIGH.svg", "target/RENDER_V2_HIGH.png", 900, 900);
                renderPng("target/V2_MED.svg", "target/RENDER_V2_MED.png", 900, 900);
                renderPng("target/V2_LOW.svg", "target/RENDER_V2_LOW.png", 900, 900);
                renderPng("target/V2_CUSTOM_2.svg", "target/RENDER_V2_CUSTOM_2.png", 900, 900);
                renderPng("target/V2_CUSTOM_5.svg", "target/RENDER_V2_CUSTOM_5.png", 900, 900);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await(30, TimeUnit.SECONDS);
    }

    public static String traceWithCustomColors(BufferedImage img, int numColors, float ltres, float qtres, float pathomit, float strokeWidth) throws Exception {
        HashMap<String, Float> options = ImageTracer.checkoptions(null);
        options.put("numberofcolors", (float) numColors);
        options.put("colorquantcycles", 1f);
        options.put("colorsampling", 1f);
        options.put("mincolorratio", 0f);
        options.put("ltres", ltres);
        options.put("qtres", qtres);
        options.put("pathomit", pathomit);
        options.put("strokewidth", strokeWidth);
        options.put("viewbox", 1f);
        options.put("scale", 1f);
        options.put("roundcoords", 1f);

        List<Color> detectedColors = ColorDetector.extractDominantColors(img, numColors);
        System.out.println("Custom " + numColors + " colors: " + detectedColors);
        byte[][] tracerPal = PaletteQuantizer.toTracerPalette(detectedColors);

        ImageTracer.ImageData imgd = ImageTracer.loadImageData(img);
        ImageTracer.IndexedImage ii = ImageTracer.colorquantization(imgd, tracerPal, options);

        int[][][] rawlayers = ImageTracer.layering(ii);
        ArrayList<ArrayList<ArrayList<Integer[]>>> bps = ImageTracer.batchpathscan(rawlayers, (int) Math.floor(pathomit));
        ArrayList<ArrayList<ArrayList<Double[]>>> bis = batchinternodesEnhanced(bps);
        ii.layers = batchtracelayersGeometric(bis, ltres, qtres);

        return generateSvgNoCollisions(ii, options);
    }

    public static String traceWithSettings(BufferedImage img, int numColors, float ltres, float qtres, float pathomit, float strokeWidth) throws Exception {
        HashMap<String, Float> options = ImageTracer.checkoptions(null);
        options.put("numberofcolors", (float) numColors);
        options.put("colorquantcycles", 3f);
        options.put("colorsampling", 1f);
        options.put("mincolorratio", 0.005f);
        options.put("ltres", ltres);
        options.put("qtres", qtres);
        options.put("pathomit", pathomit);
        options.put("strokewidth", strokeWidth);
        options.put("viewbox", 1f);
        options.put("scale", 1f);
        options.put("roundcoords", 1f);

        ImageTracer.ImageData imgd = ImageTracer.loadImageData(img);
        ImageTracer.IndexedImage ii = ImageTracer.colorquantization(imgd, null, options);

        int[][][] rawlayers = ImageTracer.layering(ii);
        ArrayList<ArrayList<ArrayList<Integer[]>>> bps = ImageTracer.batchpathscan(rawlayers, (int) Math.floor(pathomit));
        ArrayList<ArrayList<ArrayList<Double[]>>> bis = batchinternodesEnhanced(bps);
        ii.layers = batchtracelayersGeometric(bis, ltres, qtres);

        return generateSvgNoCollisions(ii, options);
    }

    private static void renderPng(String svgPath, String pngPath, int w, int h) throws Exception {
        String svg = Files.readString(new File(svgPath).toPath());
        Group group = SvgViewRenderer.renderSvgToGroup(svg);
        Scene scene = new Scene(group, w, h, javafx.scene.paint.Color.WHITE);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.WHITE);
        WritableImage fxImg = group.snapshot(params, null);
        int imgW = (int) fxImg.getWidth(), imgH = (int) fxImg.getHeight();
        PixelReader pr = fxImg.getPixelReader();
        BufferedImage bImg = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                bImg.setRGB(x, y, pr.getArgb(x, y));
            }
        }
        ImageIO.write(bImg, "png", new File(pngPath));
        System.out.println("Rendered: " + pngPath);
    }

    public static String tracePipeline(BufferedImage img, List<Color> paletteColors, float ltres, float qtres, float pathomit, float strokeWidth) throws Exception {
        HashMap<String, Float> options = ImageTracer.checkoptions(null);
        options.put("ltres", ltres);
        options.put("qtres", qtres);
        options.put("pathomit", pathomit);
        options.put("strokewidth", strokeWidth);
        options.put("viewbox", 1f);
        options.put("scale", 1f);
        options.put("roundcoords", 1f);

        byte[][] tracerPal = PaletteQuantizer.toTracerPalette(paletteColors);
        ImageTracer.ImageData imgd = ImageTracer.loadImageData(img);
        ImageTracer.IndexedImage ii = ImageTracer.colorquantization(imgd, tracerPal, options);

        int[][][] rawlayers = ImageTracer.layering(ii);
        ArrayList<ArrayList<ArrayList<Integer[]>>> bps = ImageTracer.batchpathscan(rawlayers, (int) Math.floor(pathomit));
        ArrayList<ArrayList<ArrayList<Double[]>>> bis = batchinternodesEnhanced(bps);
        ii.layers = batchtracelayersGeometric(bis, ltres, qtres);

        // Render layers ordered by area (most pixels first to least pixels on top)
        return buildSvgLayerOrder(ii, options);
    }

    private static ArrayList<ArrayList<ArrayList<Double[]>>> batchinternodesEnhanced(ArrayList<ArrayList<ArrayList<Integer[]>>> bpaths) {
        ArrayList<ArrayList<ArrayList<Double[]>>> binternodes = new ArrayList<>();
        for (ArrayList<ArrayList<Integer[]>> bpath : bpaths) {
            ArrayList<ArrayList<Double[]>> ins = new ArrayList<>();
            for (ArrayList<Integer[]> path : bpath) {
                ArrayList<Double[]> thisinp = new ArrayList<>();
                int palen = path.size();
                for (int pcnt = 0; pcnt < palen; pcnt++) {
                    int previdx = (pcnt - 1 + palen) % palen;
                    int nextidx = (pcnt + 1) % palen;
                    Integer[] ppPrev = path.get(previdx);
                    Integer[] ppCur = path.get(pcnt);
                    int kLook = Math.min(3, palen / 4);
                    int prevK = (pcnt - kLook + palen) % palen;
                    int nextK = (pcnt + kLook) % palen;
                    Integer[] ppPrevK = path.get(prevK);
                    Integer[] ppNextK = path.get(nextK);

                    double v1x = ppCur[0] - ppPrevK[0], v1y = ppCur[1] - ppPrevK[1];
                    double v2x = ppNextK[0] - ppCur[0], v2y = ppNextK[1] - ppCur[1];
                    double len1 = Math.sqrt(v1x * v1x + v1y * v1y);
                    double len2 = Math.sqrt(v2x * v2x + v2y * v2y);
                    
                    boolean isCorner = false;
                    if (len1 > 1e-4 && len2 > 1e-4) {
                        double cosTheta = (v1x * v2x + v1y * v2y) / (len1 * len2);
                        // Significant turn (>= 55 degrees) indicates an intentional geometric corner
                        isCorner = (cosTheta < 0.57);
                    }

                    Double[] pMid1 = new Double[3];
                    pMid1[0] = (ppPrev[0] + ppCur[0]) / 2.0;
                    pMid1[1] = (ppPrev[1] + ppCur[1]) / 2.0;
                    pMid1[2] = 0.0;
                    thisinp.add(pMid1);

                    if (isCorner) {
                        Double[] pCorner = new Double[3];
                        pCorner[0] = (double) ppCur[0];
                        pCorner[1] = (double) ppCur[1];
                        pCorner[2] = 0.0;
                        thisinp.add(pCorner);
                    }
                }
                
                int inplen = thisinp.size();
                for (int i = 0; i < inplen; i++) {
                    Double[] cur = thisinp.get(i);
                    Double[] next = thisinp.get((i + 1) % inplen);
                    cur[2] = getDirection(cur[0], cur[1], next[0], next[1]);
                }
                ins.add(thisinp);
            }
            binternodes.add(ins);
        }
        return binternodes;
    }

    private static double getDirection(double x1, double y1, double x2, double y2) {
        if (x1 < x2) {
            if (y1 < y2) return 1.0;
            else if (y1 > y2) return 7.0;
            else return 0.0;
        } else if (x1 > x2) {
            if (y1 < y2) return 3.0;
            else if (y1 > y2) return 5.0;
            else return 4.0;
        } else {
            if (y1 < y2) return 2.0;
            else if (y1 > y2) return 6.0;
            else return 8.0;
        }
    }

    private static ArrayList<ArrayList<ArrayList<Double[]>>> batchtracelayersGeometric(ArrayList<ArrayList<ArrayList<Double[]>>> binternodes, float ltres, float qtres) {
        ArrayList<ArrayList<ArrayList<Double[]>>> res = new ArrayList<>();
        for (ArrayList<ArrayList<Double[]>> layer : binternodes) {
            ArrayList<ArrayList<Double[]>> tracedLayer = new ArrayList<>();
            for (ArrayList<Double[]> path : layer) {
                tracedLayer.add(tracepathGeometric(path, ltres, qtres));
            }
            res.add(tracedLayer);
        }
        return res;
    }

    private static ArrayList<Double[]> tracepathGeometric(ArrayList<Double[]> path, float ltres, float qtres) {
        int pcnt = 0, seqend = 0;
        double segtype1, segtype2;
        ArrayList<Double[]> smp = new ArrayList<>();
        int pathlength = path.size();
        if (pathlength < 3) return smp;

        while (pcnt < pathlength) {
            segtype1 = path.get(pcnt)[2];
            segtype2 = -1;
            seqend = pcnt + 1;
            while (((path.get(seqend)[2] == segtype1) || (path.get(seqend)[2] == segtype2) || (segtype2 == -1))
                    && (seqend < (pathlength - 1))) {
                if ((path.get(seqend)[2] != segtype1) && (segtype2 == -1)) {
                    segtype2 = path.get(seqend)[2];
                }
                seqend++;
            }
            if (seqend == (pathlength - 1)) {
                seqend = 0;
            }

            smp.addAll(fitseqGeometric(path, ltres, qtres, pcnt, seqend));

            if (seqend > 0) pcnt = seqend;
            else pcnt = pathlength;
        }
        return smp;
    }

    private static ArrayList<Double[]> fitseqGeometric(ArrayList<Double[]> path, float ltres, float qtres, int seqstart, int seqend) {
        ArrayList<Double[]> segment = new ArrayList<>();
        int pathlength = path.size();
        if (seqend > pathlength || seqend < 0) return segment;

        double x1 = path.get(seqstart)[0], y1 = path.get(seqstart)[1];
        double x2 = path.get(seqend)[0], y2 = path.get(seqend)[1];
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;

        int count = seqend - seqstart;
        if (count < 0) count += pathlength;
        if (count == 0) return segment;

        double[] cumDist = new double[count + 1];
        cumDist[0] = 0;
        for (int i = 1; i <= count; i++) {
            int pPrev = (seqstart + i - 1) % pathlength;
            int pCur = (seqstart + i) % pathlength;
            double dpx = path.get(pCur)[0] - path.get(pPrev)[0];
            double dpy = path.get(pCur)[1] - path.get(pPrev)[1];
            cumDist[i] = cumDist[i - 1] + Math.sqrt(dpx * dpx + dpy * dpy);
        }
        double totalChord = cumDist[count];
        if (totalChord < 1e-6) totalChord = 1e-6;

        boolean linePass = true;
        int errorpoint = seqstart;
        double maxError = 0.0;
        double ltresSq = ltres * ltres;

        for (int i = 1; i < count; i++) {
            int pCur = (seqstart + i) % pathlength;
            double px = path.get(pCur)[0], py = path.get(pCur)[1];
            double distSq;
            if (lenSq < 1e-9) {
                distSq = (px - x1) * (px - x1) + (py - y1) * (py - y1);
            } else {
                double u = ((px - x1) * dx + (py - y1) * dy) / lenSq;
                if (u < 0.0) u = 0.0;
                else if (u > 1.0) u = 1.0;
                double projX = x1 + u * dx;
                double projY = y1 + u * dy;
                distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY);
            }

            if (distSq > ltresSq) linePass = false;
            if (distSq > maxError) {
                maxError = distSq;
                errorpoint = pCur;
            }
        }

        if (linePass) {
            Double[] seg = new Double[7];
            seg[0] = 1.0;
            seg[1] = x1; seg[2] = y1;
            seg[3] = x2; seg[4] = y2;
            seg[5] = 0.0; seg[6] = 0.0;
            segment.add(seg);
            return segment;
        }

        int fitpoint = errorpoint;
        int fitIndex = fitpoint - seqstart;
        if (fitIndex < 0) fitIndex += pathlength;
        double tFit = cumDist[fitIndex] / totalChord;
        if (tFit < 0.05) tFit = 0.05;
        if (tFit > 0.95) tFit = 0.95;

        double t1 = (1.0 - tFit) * (1.0 - tFit);
        double t2 = 2.0 * (1.0 - tFit) * tFit;
        double t3 = tFit * tFit;
        double cpx = (((t1 * x1) + (t3 * x2)) - path.get(fitpoint)[0]) / -t2;
        double cpy = (((t1 * y1) + (t3 * y2)) - path.get(fitpoint)[1]) / -t2;

        boolean curvePass = true;
        maxError = 0.0;
        errorpoint = seqstart;
        double qtresSq = qtres * qtres;

        for (int i = 1; i < count; i++) {
            int pCur = (seqstart + i) % pathlength;
            double t = cumDist[i] / totalChord;
            double b1 = (1.0 - t) * (1.0 - t);
            double b2 = 2.0 * (1.0 - t) * t;
            double b3 = t * t;
            double bx = (b1 * x1) + (b2 * cpx) + (b3 * x2);
            double by = (b1 * y1) + (b2 * cpy) + (b3 * y2);

            double px = path.get(pCur)[0], py = path.get(pCur)[1];
            double distSq = (px - bx) * (px - bx) + (py - by) * (py - by);

            if (distSq > qtresSq) curvePass = false;
            if (distSq > maxError) {
                maxError = distSq;
                errorpoint = pCur;
            }
        }

        if (curvePass) {
            Double[] seg = new Double[7];
            seg[0] = 2.0;
            seg[1] = x1; seg[2] = y1;
            seg[3] = cpx; seg[4] = cpy;
            seg[5] = x2; seg[6] = y2;
            segment.add(seg);
            return segment;
        }

        int splitpoint = (errorpoint != seqstart && errorpoint != seqend) ? errorpoint : fitpoint;
        if (splitpoint == seqstart || splitpoint == seqend) {
            splitpoint = (seqstart + (count / 2)) % pathlength;
        }

        segment = fitseqGeometric(path, ltres, qtres, seqstart, splitpoint);
        segment.addAll(fitseqGeometric(path, ltres, qtres, splitpoint, seqend));
        return segment;
    }

    // Sort layers by area: largest layer (background) first, smallest (foreground text) last
    private static String buildSvgLayerOrder(ImageTracer.IndexedImage ii, HashMap<String, Float> options) {
        int w = (int) (ii.width * options.get("scale"));
        int h = (int) (ii.height * options.get("scale"));
        StringBuilder sb = new StringBuilder("<svg viewBox=\"0 0 " + w + " " + h + "\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" >");

        // Count pixels per layer
        int[] layerPixelCounts = new int[ii.palette.length];
        for (int y = 1; y <= ii.height; y++) {
            for (int x = 1; x <= ii.width; x++) {
                int l = ii.array[y][x];
                if (l >= 0 && l < layerPixelCounts.length) {
                    layerPixelCounts[l]++;
                }
            }
        }

        // Order layers: descending by pixel count (largest layer at bottom)
        Integer[] layerOrder = new Integer[ii.palette.length];
        for (int i = 0; i < layerOrder.length; i++) layerOrder[i] = i;
        Arrays.sort(layerOrder, (a, b) -> Integer.compare(layerPixelCounts[b], layerPixelCounts[a]));

        for (int layerIdx : layerOrder) {
            if (layerPixelCounts[layerIdx] == 0) continue;
            ArrayList<ArrayList<Double[]>> layerPaths = ii.layers.get(layerIdx);
            for (ArrayList<Double[]> path : layerPaths) {
                if (path.isEmpty()) continue;
                ImageTracer.svgpathstring(sb, "", path, ImageTracer.tosvgcolorstr(ii.palette[layerIdx], options), options);
            }
        }

        sb.append("</svg>");
        return sb.toString();
    }

    private static String generateSvgNoCollisions(ImageTracer.IndexedImage ii, HashMap<String, Float> options) {
        int w = (int) (ii.width * options.get("scale"));
        int h = (int) (ii.height * options.get("scale"));
        String viewboxorviewport = "viewBox=\"0 0 " + w + " " + h + "\" ";
        StringBuilder svgstr = new StringBuilder("<svg " + viewboxorviewport + "version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" >");

        TreeMap<Double, List<Integer[]>> zindex = new TreeMap<>();
        for (int k = 0; k < ii.layers.size(); k++) {
            for (int pcnt = 0; pcnt < ii.layers.get(k).size(); pcnt++) {
                double label = (ii.layers.get(k).get(pcnt).get(0)[2] * w) + ii.layers.get(k).get(pcnt).get(0)[1];
                zindex.computeIfAbsent(label, l -> new ArrayList<>()).add(new Integer[]{k, pcnt});
            }
        }

        for (Map.Entry<Double, List<Integer[]>> entry : zindex.entrySet()) {
            for (Integer[] kp : entry.getValue()) {
                int layerIdx = kp[0];
                int pathIdx = kp[1];
                ImageTracer.svgpathstring(svgstr,
                        "",
                        ii.layers.get(layerIdx).get(pathIdx),
                        ImageTracer.tosvgcolorstr(ii.palette[layerIdx], options),
                        options);
            }
        }

        svgstr.append("</svg>");
        return svgstr.toString();
    }
}
