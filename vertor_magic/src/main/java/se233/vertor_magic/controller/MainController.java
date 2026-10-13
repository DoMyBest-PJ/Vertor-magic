package se233.vertor_magic.controller;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import se233.vertor_magic.model.ColorMode;
import se233.vertor_magic.model.ConversionSettings;
import se233.vertor_magic.model.DetailLevel;
import se233.vertor_magic.model.ImageItem;
import se233.vertor_magic.service.*;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Main Editor Controller for main-view.fxml.
 * Coordinates the Main Editor, Navigation, Settings Configuration,
 * Zoom Controls, Batch Preprocessing, and Export All.
 */
public class MainController implements Initializable {

    @FXML private BorderPane editorContainer;
    @FXML private HBox topToolBar;
    @FXML private Button btnZoomIn;
    @FXML private Button btnZoomOut;
    @FXML private Button btnZoomFit;

    @FXML private SplitPane centerSplitPane;
    @FXML private ScrollPane originalScrollPane;
    @FXML private StackPane originalContainer;
    @FXML private ImageView originalImageView;
    @FXML private Label lblOriginalInfo;

    @FXML private ScrollPane vectorScrollPane;
    @FXML private StackPane vectorContainer;
    @FXML private Group vectorGroup;
    @FXML private Label lblVectorInfo;

    @FXML private Button btnBack;
    @FXML private Label lblImageCounter;
    @FXML private Button btnNext;
    @FXML private Label lblCurrentFileName;

    @FXML private RadioButton rbLow;
    @FXML private RadioButton rbMedium;
    @FXML private RadioButton rbHigh;
    @FXML private ToggleGroup tgDetail;

    @FXML private RadioButton rbUnlimited;
    @FXML private RadioButton rbCustom;
    @FXML private ToggleGroup tgColor;
    @FXML private VBox customColorsBox;
    @FXML private Label lblDetectedColors;
    @FXML private Label lblColorLimitHint;
    @FXML private Spinner<Integer> spnColorCount;

    @FXML private CheckBox chkRemoveBackground;

    @FXML private Button btnResetSettings;
    @FXML private Button btnHistory;
    @FXML private Button btnExportAll;
    @FXML private Button btnImportMore;

    @FXML private Label lblStatus;
    @FXML private Label lblParallelStatus;
    @FXML private ProgressBar progressBar;

    private final List<ImageItem> imageItems = new ArrayList<>();
    private int currentIndex = 0;
    private double currentZoomFactor = 1.0;

    // Image Drag & Pan State
    private double dragStartSceneX;
    private double dragStartSceneY;
    private double dragStartH;
    private double dragStartV;
    private boolean isSyncingScroll = false;

    private final BatchProcessingService batchProcessingService = new BatchProcessingService();
    private final FileExportService fileExportService = new FileExportService();
    private Task<Void> activeTracingTask;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Configure Spinner value factory
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 5, 2);
        spnColorCount.setValueFactory(factory);
        spnColorCount.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                onSettingChanged();
            }
        });

        // Initialize Parallel status badge
        updateParallelBadge();

        // Setup image click-and-drag panning and scroll synchronization
        setupImagePanning();
    }

    // =========================================================================
    // FILE IMPORT INTO EDITOR
    // =========================================================================

    @FXML
    public void onImportMoreClicked(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image or ZIP Files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported Files (*.png, *.jpg, *.zip)", "*.png", "*.jpg", "*.jpeg", "*.zip"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("ZIP Archives", "*.zip")
        );
        List<File> selected = chooser.showOpenMultipleDialog(editorContainer.getScene().getWindow());
        if (selected != null && !selected.isEmpty()) {
            importFiles(selected);
        }
    }

    /**
     * Imports a list of image or ZIP files into the editor.
     * Called by DropZoneController when transitioning from drop-zone-view.fxml,
     * or by onImportMoreClicked inside the editor.
     *
     * @param rawFiles List of selected or dropped files
     * @return true if at least one valid image was loaded
     */
    public boolean importFiles(List<File> rawFiles) {
        if (rawFiles == null || rawFiles.isEmpty()) return false;

        List<File> imagesToLoad = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (File f : rawFiles) {
            if (ZipService.isZipFile(f)) {
                try {
                    List<File> extracted = ZipService.extractImages(f);
                    imagesToLoad.addAll(extracted);
                } catch (Exception e) {
                    errors.add("Failed to extract ZIP " + f.getName() + ": " + e.getMessage());
                }
            } else if (ZipService.isImageFile(f)) {
                imagesToLoad.add(f);
            } else {
                errors.add("Unsupported file ignored: " + f.getName());
            }
        }

        if (!errors.isEmpty()) {
            showErrorAlert("File Import Notice", String.join("\n", errors));
        }

        if (imagesToLoad.isEmpty()) {
            if (imageItems.isEmpty()) {
                lblStatus.setText("No valid images imported.");
            }
            return false;
        }

        // Convert files to ImageItem objects
        List<ImageItem> newItems = new ArrayList<>();
        for (File imgFile : imagesToLoad) {
            try {
                ImageItem item = new ImageItem(imgFile);
                newItems.add(item);
            } catch (Exception e) {
                showErrorAlert("Image Load Error", "Error reading " + imgFile.getName() + ": " + e.getMessage());
            }
        }

        if (newItems.isEmpty()) return false;

        boolean wasEmpty = imageItems.isEmpty();
        imageItems.addAll(newItems);

        if (wasEmpty) {
            currentIndex = 0;
            updateEditorUI();
        }

        // Batch Preprocessing (concurrent across files, progress feedback)
        if (imageItems.size() > 1 || !imageItems.get(currentIndex).isProcessed()) {
            startBatchPreprocessing();
        }

        return true;
    }

    // =========================================================================
    // BATCH PREPROCESSING
    // =========================================================================

    private void startBatchPreprocessing() {
        progressBar.setVisible(true);
        progressBar.setProgress(0.0);

        batchProcessingService.preprocessBatch(
                imageItems,
                progress -> progressBar.setProgress(progress),
                status -> lblStatus.setText(status),
                completedItem -> {
                    // If the completed item is the currently selected image, refresh its display
                    if (currentIndex < imageItems.size() && imageItems.get(currentIndex) == completedItem) {
                        displayCurrentImage();
                    }
                },
                () -> {
                    progressBar.setProgress(1.0);
                    updateNavigationButtons();
                    displayCurrentImage();
                }
        );
    }

    // =========================================================================
    // TOP TOOLBAR: ZOOM CONTROLS (ZOOM IN, ZOOM OUT, ZOOM TO FIT)
    // =========================================================================

    @FXML
    public void onZoomInClicked(ActionEvent event) {
        currentZoomFactor = Math.min(5.0, currentZoomFactor * 1.25);
        applyZoom();
    }

    @FXML
    public void onZoomOutClicked(ActionEvent event) {
        currentZoomFactor = Math.max(0.2, currentZoomFactor / 1.25);
        applyZoom();
    }

    @FXML
    public void onZoomFitClicked(ActionEvent event) {
        currentZoomFactor = 1.0;
        applyZoom();
        centerScrollPanes();
    }

    private void centerScrollPanes() {
        originalScrollPane.setHvalue(0.5);
        originalScrollPane.setVvalue(0.5);
        vectorScrollPane.setHvalue(0.5);
        vectorScrollPane.setVvalue(0.5);
    }

    private void applyZoom() {
        if (imageItems.isEmpty() || currentIndex >= imageItems.size()) return;
        ImageItem current = imageItems.get(currentIndex);

        // Zoom original image
        if (current.getFxImage() != null) {
            double origW = current.getFxImage().getWidth();
            double origH = current.getFxImage().getHeight();
            originalImageView.setFitWidth(origW * currentZoomFactor);
            originalImageView.setFitHeight(origH * currentZoomFactor);
        }

        // Zoom vector preview in Group
        if (vectorGroup != null) {
            vectorGroup.setScaleX(currentZoomFactor);
            vectorGroup.setScaleY(currentZoomFactor);
        }
    }

    // =========================================================================
    // IMAGE PANNING (CLICK & DRAG TO SCROLL) & PREVIEW SYNCHRONIZATION
    // =========================================================================

    private void setupImagePanning() {
        // Synchronize horizontal and vertical scrolling between both previews
        originalScrollPane.hvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSyncingScroll && newVal != null) {
                isSyncingScroll = true;
                vectorScrollPane.setHvalue(newVal.doubleValue());
                isSyncingScroll = false;
            }
        });
        originalScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSyncingScroll && newVal != null) {
                isSyncingScroll = true;
                vectorScrollPane.setVvalue(newVal.doubleValue());
                isSyncingScroll = false;
            }
        });

        vectorScrollPane.hvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSyncingScroll && newVal != null) {
                isSyncingScroll = true;
                originalScrollPane.setHvalue(newVal.doubleValue());
                isSyncingScroll = false;
            }
        });
        vectorScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSyncingScroll && newVal != null) {
                isSyncingScroll = true;
                originalScrollPane.setVvalue(newVal.doubleValue());
                isSyncingScroll = false;
            }
        });

        // Configure click-and-drag panning on both preview canvas containers
        configurePanHandlers(originalContainer, originalScrollPane);
        configurePanHandlers(vectorContainer, vectorScrollPane);
    }

    private void configurePanHandlers(StackPane container, ScrollPane scrollPane) {
        if (container == null) return;
        container.setCursor(Cursor.OPEN_HAND);

        container.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isPrimaryButtonDown()) {
                if (originalContainer != null) originalContainer.setCursor(Cursor.CLOSED_HAND);
                if (vectorContainer != null) vectorContainer.setCursor(Cursor.CLOSED_HAND);

                dragStartSceneX = event.getSceneX();
                dragStartSceneY = event.getSceneY();
                dragStartH = scrollPane.getHvalue();
                dragStartV = scrollPane.getVvalue();
                event.consume();
            }
        });

        container.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getSceneX() - dragStartSceneX;
                double deltaY = event.getSceneY() - dragStartSceneY;

                double scrollableW = container.getLayoutBounds().getWidth() - scrollPane.getViewportBounds().getWidth();
                double scrollableH = container.getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight();

                if (scrollableW > 0) {
                    double newH = dragStartH - (deltaX / scrollableW);
                    newH = Math.max(0.0, Math.min(1.0, newH));
                    originalScrollPane.setHvalue(newH);
                    vectorScrollPane.setHvalue(newH);
                }

                if (scrollableH > 0) {
                    double newV = dragStartV - (deltaY / scrollableH);
                    newV = Math.max(0.0, Math.min(1.0, newV));
                    originalScrollPane.setVvalue(newV);
                    vectorScrollPane.setVvalue(newV);
                }
                event.consume();
            }
        });

        container.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (originalContainer != null) originalContainer.setCursor(Cursor.OPEN_HAND);
            if (vectorContainer != null) vectorContainer.setCursor(Cursor.OPEN_HAND);
            event.consume();
        });
    }

    // =========================================================================
    // IMAGE NAVIGATION (BACK & NEXT)
    // =========================================================================

    @FXML
    public void onBackClicked(ActionEvent event) {
        if (currentIndex > 0) {
            currentIndex--;
            updateEditorUI();
        }
    }

    @FXML
    public void onNextClicked(ActionEvent event) {
        if (currentIndex < imageItems.size() - 1) {
            currentIndex++;
            updateEditorUI();
        }
    }

    private void updateNavigationButtons() {
        int total = imageItems.size();
        btnBack.setDisable(currentIndex <= 0);
        btnNext.setDisable(currentIndex >= total - 1);
        lblImageCounter.setText((currentIndex + 1) + " / " + total);
    }

    // =========================================================================
    // CONVERSION CONFIGURATION (DETAIL, COLOR, BACKGROUND) & INSTANT PREVIEW
    // =========================================================================

    @FXML
    public void onDetailChanged(ActionEvent event) {
        onSettingChanged();
    }

    @FXML
    public void onColorModeChanged(ActionEvent event) {
        boolean isCustom = rbCustom.isSelected();
        customColorsBox.setVisible(isCustom);
        customColorsBox.setManaged(isCustom);
        onSettingChanged();
    }

    @FXML
    public void onRemoveBackgroundChanged(ActionEvent event) {
        onSettingChanged();
    }

    private void onSettingChanged() {
        if (imageItems.isEmpty() || currentIndex >= imageItems.size()) return;

        ImageItem current = imageItems.get(currentIndex);
        ConversionSettings settings = buildCurrentSettingsFromUI();
        current.setSettings(settings);

        updateParallelBadge();
        triggerInstantPreview(current);
    }

    private ConversionSettings buildCurrentSettingsFromUI() {
        DetailLevel detail;
        if (rbLow.isSelected()) detail = DetailLevel.LOW;
        else if (rbHigh.isSelected()) detail = DetailLevel.HIGH;
        else detail = DetailLevel.MEDIUM;

        ColorMode colorMode = rbCustom.isSelected() ? ColorMode.CUSTOM : ColorMode.UNLIMITED;
        int colorCount = spnColorCount.getValue() != null ? spnColorCount.getValue() : 2;
        boolean removeBg = chkRemoveBackground.isSelected();

        return new ConversionSettings(detail, colorMode, colorCount, removeBg);
    }

    /**
     * Re-traces the current image on a background task and immediately updates the preview.
     */
    private void triggerInstantPreview(ImageItem item) {
        if (activeTracingTask != null && activeTracingTask.isRunning()) {
            activeTracingTask.cancel();
        }

        lblStatus.setText("Generating instant preview...");
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        activeTracingTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                VectorizationService.processImageItem(item);
                return null;
            }

            @Override
            protected void succeeded() {
                lblStatus.setText("Preview updated.");
                progressBar.setProgress(0.0);
                displayCurrentImage();
            }

            @Override
            protected void failed() {
                lblStatus.setText("Tracing error: " + (getException() != null ? getException().getMessage() : "unknown"));
                progressBar.setProgress(0.0);
            }
        };

        new Thread(activeTracingTask, "Instant-Preview-Worker").start();
    }

    // =========================================================================
    // BATCH SVG EXPORT (EXPORT ALL)
    // =========================================================================

    @FXML
    public void onExportAllClicked(ActionEvent event) {
        if (imageItems.isEmpty()) {
            showErrorAlert("Export Notice", "No images imported to export.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder for SVG Export");
        File selectedDir = chooser.showDialog(editorContainer.getScene().getWindow());

        if (selectedDir != null) {
            progressBar.setProgress(0.0);
            ConversionSettings activeSettings = buildCurrentSettingsFromUI();

            fileExportService.exportAll(
                    imageItems,
                    selectedDir,
                    activeSettings,
                    progress -> progressBar.setProgress(progress),
                    status -> lblStatus.setText(status),
                    savedFiles -> {
                        progressBar.setProgress(1.0);
                        showExportSuccessDialog(selectedDir, savedFiles);
                    },
                    err -> showErrorAlert("Export Notice", err)
            );
        }
    }

    // =========================================================================
    // EXTRA TOOLS (RESET SETTINGS & EXPORT HISTORY)
    // =========================================================================

    @FXML
    public void onResetSettingsClicked(ActionEvent event) {
        // Restore all settings to defaults
        rbMedium.setSelected(true);
        rbCustom.setSelected(true);
        customColorsBox.setVisible(true);
        customColorsBox.setManaged(true);
        spnColorCount.getValueFactory().setValue(2);
        chkRemoveBackground.setSelected(false);

        onSettingChanged();
        lblStatus.setText("Settings reset to defaults (Medium detail, Custom 2 colors, opaque).");
    }

    @FXML
    public void onHistoryClicked(ActionEvent event) {
        List<FileExportService.ExportRecord> history = FileExportService.getExportHistory();
        if (history.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export History");
            alert.setHeaderText("No Export History Yet");
            alert.setContentText("Exported SVG files and directories will be listed here after you use Export All.");
            alert.showAndWait();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (FileExportService.ExportRecord record : history) {
            sb.append("• [").append(record.timestamp).append("]\n")
              .append("  Folder: ").append(record.outputDirectory.getAbsolutePath()).append("\n")
              .append("  Files (").append(record.exportedFiles.size()).append("): ");
            for (int i = 0; i < Math.min(3, record.exportedFiles.size()); i++) {
                sb.append(record.exportedFiles.get(i).getName()).append(", ");
            }
            if (record.exportedFiles.size() > 3) sb.append("...");
            sb.append("\n\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export History");
        alert.setHeaderText("Previous Export Sessions");
        alert.setContentText(sb.toString());

        ButtonType openLastFolder = new ButtonType("Open Last Folder");
        alert.getButtonTypes().add(openLastFolder);

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == openLastFolder) {
            try {
                Desktop.getDesktop().open(history.get(history.size() - 1).outputDirectory);
            } catch (Exception e) {
                showErrorAlert("Open Folder", "Unable to open folder: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // UI UPDATES & RENDERING
    // =========================================================================

    private void updateEditorUI() {
        if (imageItems.isEmpty()) return;

        ImageItem current = imageItems.get(currentIndex);
        lblCurrentFileName.setText(current.getOriginalFileName());
        updateNavigationButtons();

        // Detect colors if needed
        if (current.getDetectedColorCount() == 0 && current.getBufferedImage() != null) {
            int detected = ColorDetector.countUniqueColors(current.getBufferedImage());
            current.setDetectedColorCount(detected);
        }

        int detected = current.getDetectedColorCount();
        lblDetectedColors.setText("Detected Colors: " + detected);

        // Cap custom color selection limit at 5 if image contains > 5 colors
        int maxAllowed = ColorDetector.getCustomColorUpperLimit(detected);
        int currentVal = current.getSettings().getCustomColorCount();
        int safeVal = Math.min(maxAllowed, Math.max(2, currentVal));

        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, maxAllowed, safeVal);
        spnColorCount.setValueFactory(factory);

        // Sync UI controls with current item settings
        ConversionSettings s = current.getSettings();
        if (s.getDetailLevel() == DetailLevel.LOW) rbLow.setSelected(true);
        else if (s.getDetailLevel() == DetailLevel.HIGH) rbHigh.setSelected(true);
        else rbMedium.setSelected(true);

        if (s.getColorMode() == ColorMode.UNLIMITED) {
            rbUnlimited.setSelected(true);
            customColorsBox.setVisible(false);
            customColorsBox.setManaged(false);
        } else {
            rbCustom.setSelected(true);
            customColorsBox.setVisible(true);
            customColorsBox.setManaged(true);
        }

        chkRemoveBackground.setSelected(s.isRemoveBackground());

        updateParallelBadge();
        displayCurrentImage();
    }

    private void displayCurrentImage() {
        if (imageItems.isEmpty() || currentIndex >= imageItems.size()) return;
        ImageItem current = imageItems.get(currentIndex);

        // Display original image
        if (current.getFxImage() != null) {
            originalImageView.setImage(current.getFxImage());
            lblOriginalInfo.setText(String.format("%.0f x %.0f px",
                    current.getFxImage().getWidth(), current.getFxImage().getHeight()));
        }

        // Display vector SVG result in vectorGroup
        if (vectorGroup != null) {
            vectorGroup.getChildren().clear();
            if (current.getSvgContent() != null && !current.getSvgContent().isEmpty()) {
                Group rendered = SvgViewRenderer.renderSvgToGroup(current.getSvgContent());
                vectorGroup.getChildren().addAll(rendered.getChildren());
                lblVectorInfo.setText("SVG Generated (" + current.getSettings().getDetailLevel() + ", " +
                        (current.getSettings().getColorMode() == ColorMode.CUSTOM ? current.getSettings().getCustomColorCount() + " colors" : "Unlimited") + ")");
            } else {
                lblVectorInfo.setText("Processing SVG...");
            }
        }

        applyZoom();
    }

    private void updateParallelBadge() {
        ConversionSettings s = buildCurrentSettingsFromUI();
        if (s.isParallelEligible()) {
            lblParallelStatus.setText("Parallel Mode (Medium + Custom)");
            lblParallelStatus.getStyleClass().setAll("parallel-badge");
        } else {
            lblParallelStatus.setText("Sequential Mode (" + s.getDetailLevel() + " + " + s.getColorMode() + ")");
            lblParallelStatus.getStyleClass().setAll("sequential-badge");
        }
    }

    private void showExportSuccessDialog(File destDir, List<File> files) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Succeeded");
        alert.setHeaderText("Batch SVG Export Completed");
        alert.setContentText("Successfully exported " + files.size() + " SVG vector files to:\n" + destDir.getAbsolutePath());

        ButtonType btnOpen = new ButtonType("Open Folder in Explorer");
        alert.getButtonTypes().add(btnOpen);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == btnOpen) {
            try {
                Desktop.getDesktop().open(destDir);
            } catch (Exception e) {
                showErrorAlert("Open Folder", "Could not open folder: " + e.getMessage());
            }
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
