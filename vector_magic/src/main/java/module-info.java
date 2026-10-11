module se233.vector_magic {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens se233.vector_magic to javafx.fxml;
    opens se233.vector_magic.controller to javafx.fxml;
    exports se233.vector_magic;
    exports se233.vector_magic.controller;
    exports se233.vector_magic.model;
    exports se233.vector_magic.service;
}