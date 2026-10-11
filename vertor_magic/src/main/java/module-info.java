module se233.vertor_magic {
    requires javafx.controls;
    requires javafx.fxml;


    opens se233.vertor_magic to javafx.fxml;
    exports se233.vertor_magic;
}