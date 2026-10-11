module se233.vertor_magic {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens se233.vertor_magic to javafx.fxml;
    opens se233.vertor_magic.controller to javafx.fxml;
    exports se233.vertor_magic;
    exports se233.vertor_magic.controller;
    exports se233.vertor_magic.model;
    exports se233.vertor_magic.service;
}