package se233.vertor_magic.service;

import javafx.application.Platform;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.ImageItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Handles exporting all imported images as SVG files to a user-selected destination directory.
 * Implements Task 8, Task 9, and Task 12 (Export record & folder opener).
 */
public class FileExportService {

    public static class ExportRecord {
        public final String timestamp;
        public final File outputDirectory;
        public final List<File> exportedFiles;

        public ExportRecord(File outputDirectory, List<File> exportedFiles) {
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.outputDirectory = outputDirectory;
            this.exportedFiles = exportedFiles;
        }
    }

    private static final List<ExportRecord> exportHistory = new ArrayList<>();
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private ExecutorService exportExecutor;

    public static List<ExportRecord> getExportHistory() {
        return new ArrayList<>(exportHistory);
    }

    /**
     * Exports all items to the chosen destination folder.
     * Uses Task 9 conditional parallel processing:
     * - Medium + Custom (2-5 colors) -> parallel threads.
     * - Otherwise -> single sequential thread.
     */
    public void exportAll(List<ImageItem> items,
                          File outputDirectory,
                          ConversionSettings activeSettings,
                          Consumer<Double> progressCallback,
                          Consumer<String> statusCallback,
                          Consumer<List<File>> onCompletedCallback,
                          Consumer<String> onErrorCallback) {
        if (items == null || items.isEmpty()) {
            if (onErrorCallback != null) Platform.runLater(() -> onErrorCallback.accept("No images available to export."));
            return;
        }

        if (outputDirectory == null || !outputDirectory.isDirectory()) {
            if (onErrorCallback != null) Platform.runLater(() -> onErrorCallback.accept("Invalid export directory."));
            return;
        }

        isCancelled.set(false);

        // Task 9 check
        boolean isParallel = activeSettings != null && activeSettings.isParallelEligible() && items.size() > 1;
        int threadCount = isParallel ? Math.min(items.size(), Math.max(2, Runtime.getRuntime().availableProcessors())) : 1;
        String modeDesc = isParallel ? "Parallel Export (" + threadCount + " threads)" : "Sequential Export (1 thread)";

        exportExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "SVG-Export-Worker");
            t.setDaemon(true);
            return t;
        });

        int total = items.size();
        AtomicInteger completedCount = new AtomicInteger(0);
        List<File> savedFiles = new CopyOnWriteArrayList<>();

        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept("Exporting " + total + " SVG files [" + modeDesc + "]..."));
        }

        new Thread(() -> {
            List<Future<?>> futures = new ArrayList<>();

            for (ImageItem item : items) {
                if (isCancelled.get()) break;

                Future<?> future = exportExecutor.submit(() -> {
                    if (isCancelled.get()) return;

                    try {
                        // Ensure SVG content is generated with up-to-date settings
                        if (item.getSvgContent() == null || !item.isProcessed() ||
                                (activeSettings != null && !activeSettings.equals(item.getSettings()))) {
                            if (activeSettings != null) {
                                item.setSettings(new ConversionSettings(activeSettings));
                            }
                            VectorizationService.processImageItem(item);
                        }

                        // Determine destination SVG file name
                        String baseName = item.getBaseName();
                        File destFile = new File(outputDirectory, baseName + ".svg");

                        // Handle duplicate names gracefully
                        int counter = 1;
                        while (destFile.exists()) {
                            destFile = new File(outputDirectory, baseName + "_" + counter + ".svg");
                            counter++;
                        }

                        // Write SVG file with UTF-8
                        try (FileWriter writer = new FileWriter(destFile, StandardCharsets.UTF_8)) {
                            writer.write(item.getSvgContent());
                        }

                        savedFiles.add(destFile);

                    } catch (Exception e) {
                        item.setProcessingError("Export failed: " + e.getMessage());
                    }

                    if (!isCancelled.get()) {
                        int finished = completedCount.incrementAndGet();
                        double progress = (double) finished / total;

                        Platform.runLater(() -> {
                            if (progressCallback != null) progressCallback.accept(progress);
                            if (statusCallback != null) {
                                statusCallback.accept("Exported " + finished + "/" + total + " SVG files [" + modeDesc + "]");
                            }
                        });
                    }
                });
                futures.add(future);
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }

            if (exportExecutor != null && !exportExecutor.isShutdown()) {
                exportExecutor.shutdownNow();
            }

            Platform.runLater(() -> {
                if (isCancelled.get()) {
                    if (statusCallback != null) statusCallback.accept("Export cancelled by user.");
                    if (onErrorCallback != null) onErrorCallback.accept("Export was cancelled. " + savedFiles.size() + " files were saved.");
                } else {
                    exportHistory.add(new ExportRecord(outputDirectory, new ArrayList<>(savedFiles)));
                    if (statusCallback != null) {
                        statusCallback.accept("Export complete! Saved " + savedFiles.size() + " SVG files to " + outputDirectory.getName());
                    }
                    if (onCompletedCallback != null) {
                        onCompletedCallback.accept(new ArrayList<>(savedFiles));
                    }
                }
            });
        }, "SVG-Export-Dispatcher").start();
    }

    public void cancel() {
        isCancelled.set(true);
        if (exportExecutor != null && !exportExecutor.isShutdown()) {
            exportExecutor.shutdownNow();
        }
    }
}
