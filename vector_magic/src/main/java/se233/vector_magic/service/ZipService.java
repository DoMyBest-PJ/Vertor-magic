package se233.vector_magic.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles extraction of images from ZIP archives.
 */
public class ZipService {

    private static final String[] SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png"};

    /**
     * Extracts all supported images from the given ZIP file.
     *
     * @param zipFile The ZIP file to extract
     * @return List of extracted image Files
     * @throws IOException If extraction fails or no supported images found
     */
    public static List<File> extractImages(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.exists()) {
            throw new FileNotFoundException("ZIP file not found: " + (zipFile != null ? zipFile.getName() : "null"));
        }

        List<File> extractedFiles = new ArrayList<>();
        Path tempDir = Files.createTempDirectory("vector_magic_" + UUID.randomUUID().toString().substring(0, 8));

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();
                // Skip macOS resource fork files and hidden files
                if (entryName.startsWith("__MACOSX") || entryName.contains("/.") || entryName.startsWith(".")) {
                    zis.closeEntry();
                    continue;
                }

                if (isImageExtension(entryName)) {
                    // Normalize file name to avoid directory traversal
                    String simpleName = new File(entryName).getName();
                    Path targetFile = tempDir.resolve(simpleName);

                    // Ensure target is inside tempDir
                    if (!targetFile.normalize().startsWith(tempDir.normalize())) {
                        throw new IOException("Illegal ZIP entry path: " + entryName);
                    }

                    // Avoid duplicate filenames in zip
                    int counter = 1;
                    while (Files.exists(targetFile)) {
                        String nameWithoutExt = simpleName.substring(0, simpleName.lastIndexOf('.'));
                        String ext = simpleName.substring(simpleName.lastIndexOf('.'));
                        targetFile = tempDir.resolve(nameWithoutExt + "_" + counter + ext);
                        counter++;
                    }

                    try (OutputStream os = new FileOutputStream(targetFile.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }

                    extractedFiles.add(targetFile.toFile());
                }
                zis.closeEntry();
            }
        }

        if (extractedFiles.isEmpty()) {
            throw new IOException("No supported JPG or PNG images found inside ZIP archive: " + zipFile.getName());
        }

        return extractedFiles;
    }

    public static boolean isZipFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".zip");
    }

    public static boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;
        return isImageExtension(file.getName());
    }

    private static boolean isImageExtension(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
