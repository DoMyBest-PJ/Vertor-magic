package se233.vertor_magic.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import se233.vertor_magic.Launcher;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Controller for drop-zone-view.fxml (Task 1: Initial File Input & Drag-and-Drop Screen).
 * Connects and transitions to main-view.fxml (MainController) once valid files are imported.
 */
public class DropZoneController {

    @FXML private VBox dropZoneContainer;
    @FXML private VBox dropCard;
    @FXML private Button btnBrowseFiles;
    @FXML private Button btnBrowseZip;

    @FXML
    public void onDropZoneOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!dropCard.getStyleClass().contains("drop-card-active")) {
                dropCard.getStyleClass().add("drop-card-active");
            }
        }
        event.consume();
    }

    @FXML
    public void onDropZoneExited(DragEvent event) {
        dropCard.getStyleClass().remove("drop-card-active");
        event.consume();
    }

    @FXML
    public void onDropZoneDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            transitionToMainEditor(files);
            success = true;
        }
        event.setDropCompleted(success);
        dropCard.getStyleClass().remove("drop-card-active");
        event.consume();
    }

    @FXML
    public void onBrowseFilesClicked(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image Files (JPG / PNG)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg")
        );
        List<File> selected = chooser.showOpenMultipleDialog(dropZoneContainer.getScene().getWindow());
        if (selected != null && !selected.isEmpty()) {
            transitionToMainEditor(selected);
        }
    }

    @FXML
    public void onBrowseZipClicked(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select ZIP Archive");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP Archive (*.zip)", "*.zip")
        );
        File zipFile = chooser.showOpenDialog(dropZoneContainer.getScene().getWindow());
        if (zipFile != null) {
            transitionToMainEditor(Collections.singletonList(zipFile));
        }
    }

    private void transitionToMainEditor(List<File> files) {
        try {
            FXMLLoader loader = new FXMLLoader(Launcher.class.getResource("main-view.fxml"));
            Parent editorRoot = loader.load();
            MainController mainController = loader.getController();

            boolean loadedSuccessfully = mainController.importFiles(files);
            if (loadedSuccessfully) {
                dropZoneContainer.getScene().setRoot(editorRoot);
            }
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Navigation Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to load Main Editor view: " + e.getMessage());
            alert.showAndWait();
        }
    }
}
