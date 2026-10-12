package se233.vertor_magic.service;

import javafx.application.Platform;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.ImageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service managing batch preprocessing and conditional parallel batch conversion.
 */
public class BatchProcessingService {

    private ExecutorService currentExecutor;

    /**
     * Batch preprocesses a list of ImageItems with default settings (Medium detail, 2 Custom colors).
     */
    public void preprocessBatch(List<ImageItem> items,
                                Consumer<Double> progressCallback,
                                Consumer<String> statusCallback,
                                Consumer<ImageItem> itemCompletedCallback,
                                Runnable onFinishedCallback) {
        processBatch(items, new ConversionSettings(), progressCallback, statusCallback, itemCompletedCallback, onFinishedCallback);
    }

    /**
     * Executes batch conversion on ImageItems using Conditional Parallel Processing.
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

        ConversionSettings effectiveSettings = settings != null ? settings : new ConversionSettings();

        // Conditional Parallel Processing evaluation:
        // Concurrent ONLY WHEN BOTH conditions are satisfied:
        // 1. Detail level is Medium.
        // 2. Color mode is Custom, with color limit up to 5 and default 2.
        boolean qualifyParallel = effectiveSettings.isParallelEligible() && items.size() > 1;

        int threadCount = qualifyParallel ? Math.min(items.size(), Math.max(2, Runtime.getRuntime().availableProcessors())) : 1;
        String modeDescription = qualifyParallel
                ? "Parallel (" + threadCount + " threads: Medium + Custom " + effectiveSettings.getCustomColorCount() + " colors)"
                : "Sequential (1 thread: " + effectiveSettings.getDetailLevel() + " + " + effectiveSettings.getColorMode() + ")";

        currentExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "Batch-Processor-Thread");
            t.setDaemon(true);
            return t;
        });

        int total = items.size();
        AtomicInteger completedCount = new AtomicInteger(0);

        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept("Starting batch processing: " + total + " images [" + modeDescription + "]..."));
        }

        // Run dispatcher on background thread to keep UI completely responsive
        new Thread(() -> {
            List<Future<?>> futures = new ArrayList<>();

            for (ImageItem item : items) {
                Future<?> future = currentExecutor.submit(() -> {
                    try {
                        if (item.getSettings() == null) {
                            item.setSettings(new ConversionSettings(effectiveSettings));
                        }
                        VectorizationService.processImageItem(item);
                    } catch (Exception e) {
                        item.setProcessingError(e.getMessage());
                    }

                    int finished = completedCount.incrementAndGet();
                    double progress = (double) finished / total;

                    Platform.runLater(() -> {
                        if (progressCallback != null) progressCallback.accept(progress);
                        if (statusCallback != null) {
                            statusCallback.accept("Processed " + finished + "/" + total + " images [" + modeDescription + "]");
                        }
                        if (itemCompletedCallback != null) {
                            itemCompletedCallback.accept(item);
                        }
                    });
                });
                futures.add(future);
            }

            // Wait for all tasks to complete
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }

            shutdownExecutor();

            Platform.runLater(() -> {
                if (statusCallback != null) {
                    statusCallback.accept("Processing complete (" + total + " images) [" + modeDescription + "].");
                }
                if (onFinishedCallback != null) onFinishedCallback.run();
            });
        }, "Batch-Dispatcher-Thread").start();
    }

    private void shutdownExecutor() {
        if (currentExecutor != null && !currentExecutor.isShutdown()) {
            currentExecutor.shutdownNow();
        }
    }
}
