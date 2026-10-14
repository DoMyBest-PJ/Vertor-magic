package se233.vertor_magic.model;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an imported image file, its cached representations,
 * conversion settings, and generated SVG output.
 */
public class ImageItem {
    private final File sourceFile;
    private final String originalFileName;
    private BufferedImage bufferedImage;
    private Image fxImage;
    private String svgContent;
    private int detectedColorCount;
    private List<Color> detectedPalette = new ArrayList<>();
    private ConversionSettings settings;
    private boolean isProcessed;
    private String processingError;

    public ImageItem(File file) throws IOException {
        this.sourceFile = file;
        this.originalFileName = file.getName();
        this.bufferedImage = ImageIO.read(file);
        if (this.bufferedImage == null) {
            throw new IOException("Unsupported or corrupt image file: " + file.getName());
        }
        this.fxImage = new Image(file.toURI().toString());
        this.settings = new ConversionSettings();
        this.isProcessed = false;
    }


    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getBaseName() {
        int dotIdx = originalFileName.lastIndexOf('.');
        return dotIdx > 0 ? originalFileName.substring(0, dotIdx) : originalFileName;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }



    public Image getFxImage() {
        return fxImage;
    }

    public String getSvgContent() {
        return svgContent;
    }

    public void setSvgContent(String svgContent) {
        this.svgContent = svgContent;
        this.isProcessed = svgContent != null && !svgContent.isEmpty();
    }

    public int getDetectedColorCount() {
        return detectedColorCount;
    }

    public void setDetectedColorCount(int detectedColorCount) {
        this.detectedColorCount = detectedColorCount;
    }

    public List<Color> getDetectedPalette() {
        return detectedPalette;
    }

    public void setDetectedPalette(List<Color> detectedPalette) {
        this.detectedPalette = detectedPalette;
    }

    public ConversionSettings getSettings() {
        return settings;
    }

    public void setSettings(ConversionSettings settings) {
        this.settings = settings;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }
}
