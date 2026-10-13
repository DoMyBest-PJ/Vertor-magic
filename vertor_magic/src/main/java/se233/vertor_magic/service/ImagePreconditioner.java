package se233.vertor_magic.service;

import java.awt.image.BufferedImage;

/**
 * Pre-conditions raster images before vectorization by removing
 * high-frequency compression artifacts while strictly preserving and sharpening
 * high-contrast boundaries, fine text, and smooth gradients.
 */
public class ImagePreconditioner {

    /**
     * Applies an adaptive edge-directed shock and denoising filter.
     * Sharpens text and logo outlines, eliminates JPEG ringing in solid shapes,
     * and maintains smooth tonal transitions in gradient regions.
     */
    public static BufferedImage denoise(BufferedImage src, int colorTolerance) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();

        int[] srcPix = src.getRGB(0, 0, w, h, null, 0, w);
        int[] dstPix = new int[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int center = srcPix[idx];
                int ca = (center >> 24) & 0xFF;
                int cr = (center >> 16) & 0xFF;
                int cg = (center >> 8) & 0xFF;
                int cb = center & 0xFF;

                // Pure/Near white background normalization
                if (cr >= 250 && cg >= 250 && cb >= 250) {
                    dstPix[idx] = (ca << 24) | 0x00FFFFFF;
                    continue;
                }

                // 3x3 local neighborhood analysis
                int minR = cr, maxR = cr;
                int minG = cg, maxG = cg;
                int minB = cb, maxB = cb;
                int sumR = 0, sumG = 0, sumB = 0, count = 0;

                int cLum = (cr * 77 + cg * 150 + cb * 29) >> 8;
                int minLum = cLum, maxLum = cLum;
                int minRgb = center & 0x00FFFFFF;
                int maxRgb = center & 0x00FFFFFF;
                int sumLum = 0;

                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        int nRgb = srcPix[ny * w + nx];
                        int nr = (nRgb >> 16) & 0xFF;
                        int ng = (nRgb >> 8) & 0xFF;
                        int nb = nRgb & 0xFF;

                        minR = Math.min(minR, nr); maxR = Math.max(maxR, nr);
                        minG = Math.min(minG, ng); maxG = Math.max(maxG, ng);
                        minB = Math.min(minB, nb); maxB = Math.max(maxB, nb);
                        sumR += nr; sumG += ng; sumB += nb;

                        int nLum = (nr * 77 + ng * 150 + nb * 29) >> 8;
                        sumLum += nLum;
                        if (nLum < minLum) {
                            minLum = nLum;
                            minRgb = nRgb & 0x00FFFFFF;
                        }
                        if (nLum > maxLum) {
                            maxLum = nLum;
                            maxRgb = nRgb & 0x00FFFFFF;
                        }
                        count++;
                    }
                }

                int maxSpan = Math.max(maxR - minR, Math.max(maxG - minG, maxB - minB));

                // High-contrast edge (fine typography, sharp logo contours)
                if (maxSpan >= 36) {
                    int meanLum = sumLum / count;
                    // Shock filter step: snap toward actual local extremum neighbor color
                    int snappedRgb = (cLum <= meanLum) ? minRgb : maxRgb;
                    dstPix[idx] = (ca << 24) | snappedRgb;
                } else if (maxSpan <= colorTolerance) {
                    // Homogeneous region: average out JPEG DCT ringing artifacts
                    dstPix[idx] = (ca << 24) | ((sumR / count) << 16) | ((sumG / count) << 8) | (sumB / count);
                } else {
                    // Subtle smooth gradient: preserve original nuanced color value
                    dstPix[idx] = center;
                }
            }
        }

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, w, h, dstPix, 0, w);
        return result;
    }
}