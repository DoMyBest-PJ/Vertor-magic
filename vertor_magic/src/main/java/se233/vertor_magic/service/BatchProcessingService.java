package se233.vertor_magic.service;

import javafx.application.Platform;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.ImageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service managing batch preprocessing and batch export.
 * Implements Task 7 (Batch Preprocessing), Task 9 (Conditional Parallel Processing),
 * and Task 12 (Cancel Processing).
 */
public class BatchProcessingService {

    private ExecutorService currentExecutor;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    /**
     * Batch preprocesses a list of ImageItems with default settings (Medium detail, 2 Custom colors).
     */
    public void preprocessBatch(List<ImageItem> items,
                                Consumer<Double> progressCallback,
                                Consumer<String> statusCallback,
                                Consumer<ImageItem> itemCompletedCallback,
                                Runnable onFinishedCallback) {
        if (items == null || items.isEmpty()) {
            if (onFinishedCallback != null) Platform.runLater(onFinishedCallback);
            return;
        }

        isCancelled.set(false);

        // Preprocessing uses default settings: Medium detail, Custom mode, 2 colors.
        // By Task 9, this satisfies: Medium + Custom with 2 colors -> Parallel processing required!
        boolean isParallel = items.size() > 1; // concurrent when multiple images
        int threadCount = isParallel ? Math.min(items.size(), Math.max(2, Runtime.getRuntime().availableProcessors())) : 1;

        currentExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "Batch-Preprocessor-Thread");
            t.setDaemon(true);
            return t;
        });

        int total = items.size();
        AtomicInteger completedCount = new AtomicInteger(0);

        String modeText = isParallel ? "Parallel (" + threadCount + " threads)" : "Sequential";
        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept("Starting preprocessing: " + total + " images [" + modeText + "]..."));
        }

        // Run dispatcher on background thread to keep UI completely responsive
        new Thread(() -> {
            List<Future<?>> futures = new ArrayList<>();

            for (ImageItem item : items) {
                if (isCancelled.get()) break;

                Future<?> future = currentExecutor.submit(() -> {
                    if (isCancelled.get()) return;

                    try {
                        // Task 7: Detect colors, configure Medium detail & 2 colors, generate preview
                        ConversionSettings s = item.getSettings();
                        if (s == null) {
                            s = new ConversionSettings();
                            item.setSettings(s);
                        }
                        VectorizationService.processImageItem(item);
                    } catch (Exception e) {
                        item.setProcessingError(e.getMessage());
                    }

                    if (!isCancelled.get()) {
                        int finished = completedCount.incrementAndGet();
                        double progress = (double) finished / total;

                        Platform.runLater(() -> {
                            if (progressCallback != null) progressCallback.accept(progress);
                            if (statusCallback != null) {
                                statusCallback.accept("Preprocessed " + finished + "/" + total + " images [" + modeText + "]");
                            }
                            if (itemCompletedCallback != null) {
                                itemCompletedCallback.accept(item);
                            }
                        });
                    }
                });
                futures.add(future);
            }

            // Wait for all tasks to complete or cancel
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }

            shutdownExecutor();

            Platform.runLater(() -> {
                if (isCancelled.get()) {
                    if (statusCallback != null) statusCallback.accept("Batch preprocessing cancelled.");
                } else {
                    if (statusCallback != null) statusCallback.accept("Preprocessing complete (" + total + " images).");
                }
                if (onFinishedCallback != null) onFinishedCallback.run();
            });
        }, "Batch-Dispatcher-Thread").start();
    }

    /**
     * Executes batch conversion on ImageItems according to Task 9 Conditional Parallel Processing.
     */
    public void processBatch(List<ImageItem> items,
                             ConversionSettings settings,
                             Consumer<Double> progressCallback,
                             Consumer<String> statusCallback,
                             Consumer<ImageItem> itemCompletedCallback,
                             Runnable onFinishedCallback) {
        if (items == null || items.isEmpty()) {
            if (onFinishedCallback != null) Platform.runLater(onFinishedCallback);
            return;
        }

        isCancelled.set(false);

        // Task 9: Conditional Parallel Processing evaluation
        // Concurrent ONLY WHEN BOTH conditions are satisfied:
        // 1. Detail level is Medium.
        // 2. Color mode is Custom, with color limit up to 5 and default 2.
        boolean qualifyParallel = settings != null && settings.isParallelEligible() && items.size() > 1;

        int threadCount = qualifyParallel ? Math.min(items.size(), Math.max(2, Runtime.getRuntime().availableProcessors())) : 1;
        String modeDescription = qualifyParallel
                ? "Parallel Processing (" + threadCount + " threads: Medium + Custom " + settings.getCustomColorCount() + " colors)"
                : "Sequential Processing (1 thread" + (settings != null ? ": " + settings.getDetailLevel() + " + " + settings.getColorMode() : "") + ")";

        currentExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "Batch-Processor-Thread");
            t.setDaemon(true);
            return t;
        });

        int total = items.size();
        AtomicInteger completedCount = new AtomicInteger(0);

        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept("Starting batch processing [" + modeDescription + "]..."));
        }

        new Thread(() -> {
            List<Future<?>> futures = new ArrayList<>();

            for (ImageItem item : items) {
                if (isCancelled.get()) break;

                Future<?> future = currentExecutor.submit(() -> {
                    if (isCancelled.get()) return;

                    try {
                        if (settings != null) {
                            item.setSettings(new ConversionSettings(settings));
                        }
                        VectorizationService.processImageItem(item);
                    } catch (Exception e) {
                        item.setProcessingError(e.getMessage());
                    }

                    if (!isCancelled.get()) {
                        int finished = completedCount.incrementAndGet();
                        double progress = (double) finished / total;

                        Platform.runLater(() -> {
                            if (progressCallback != null) progressCallback.accept(progress);
                            if (statusCallback != null) {
                                statusCallback.accept("Processed " + finished + "/" + total + " [" + modeDescription + "]");
                            }
                            if (itemCompletedCallback != null) {
                                itemCompletedCallback.accept(item);
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

            shutdownExecutor();

            Platform.runLater(() -> {
                if (isCancelled.get()) {
                    if (statusCallback != null) statusCallback.accept("Processing cancelled by user.");
                } else {
                    if (statusCallback != null) statusCallback.accept("Processing completed [" + modeDescription + "].");
                }
                if (onFinishedCallback != null) onFinishedCallback.run();
            });
        }, "Batch-Process-Dispatcher").start();
    }

    /**
     * Cancels any active batch processing (Task 12 Additional Feature).
     */
    public void cancel() {
        isCancelled.set(true);
        shutdownExecutor();
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    private void shutdownExecutor() {
        if (currentExecutor != null && !currentExecutor.isShutdown()) {
            currentExecutor.shutdownNow();
        }
    }
}
