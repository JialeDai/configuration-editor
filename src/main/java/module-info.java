module com.jidai.configurationeditor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens com.jidai.configurationeditor to javafx.fxml;
    exports com.jidai.configurationeditor;
}