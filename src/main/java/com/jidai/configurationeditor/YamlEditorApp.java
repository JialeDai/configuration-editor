package com.jidai.configurationeditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

public class YamlEditorApp extends Application {
    private TableView<YamlEntry> tableView = new TableView<>();
    private List<File> files = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        Button uploadButton = new Button("Upload YAML Files");
        uploadButton.setOnAction(e -> uploadFiles(primaryStage));

        Button saveButton = new Button("Save Changes");
        saveButton.setOnAction(e -> saveFiles());

        TableColumn<YamlEntry, String> keyColumn = new TableColumn<>("Entry");
        keyColumn.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        keyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        keyColumn.setOnEditCommit(event -> {
            YamlEntry entry = event.getRowValue();
            entry.setKey(event.getNewValue());
        });

        tableView.setEditable(true);
        tableView.getColumns().add(keyColumn);

        VBox root = new VBox(uploadButton, saveButton, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS); // Ensure tableView grows with the VBox
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setScene(scene);
        primaryStage.setTitle("YAML Editor");
        primaryStage.show();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Adjust columns when the window is resized
        scene.widthProperty().addListener((obs, oldVal, newVal) -> adjustColumnWidths());

        Platform.runLater(() -> {
            adjustColumnWidths();
            keyColumn.setPrefWidth(150); // Set initial width for the first column
        });
    }

    private void uploadFiles(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml", "*.yml"));
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null) {
            files.addAll(selectedFiles);
            parseYamlFiles();
        }
    }

    private void parseYamlFiles() {
        Yaml yaml = new Yaml();
        Map<String, Map<String, String>> allData = new HashMap<>();

        for (File file : files) {
            try (InputStream inputStream = new FileInputStream(file)) {
                Map<String, Object> data = yaml.load(inputStream);
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    allData.computeIfAbsent(entry.getKey(), k -> new HashMap<>()).put(file.getName(), formatValue(entry.getValue()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<YamlEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : allData.entrySet()) {
            YamlEntry yamlEntry = new YamlEntry(entry.getKey());
            entry.getValue().forEach(yamlEntry::setValue);
            entries.add(yamlEntry);
        }

        tableView.getItems().setAll(entries);
        addFileColumns();
    }

    private String formatValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            try {
                return YamlEntry.objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                e.printStackTrace();
                return value.toString();
            }
        }
        return value.toString();
    }

    private void addFileColumns() {
        for (File file : files) {
            String fileName = file.getName();
            TableColumn<YamlEntry, String> fileColumn = new TableColumn<>(fileName);
            fileColumn.setCellValueFactory(cellData -> cellData.getValue().valueProperty(fileName));
            fileColumn.setCellFactory(new Callback<TableColumn<YamlEntry, String>, TableCell<YamlEntry, String>>() {
                @Override
                public TableCell<YamlEntry, String> call(TableColumn<YamlEntry, String> param) {
                    return new TextAreaTableCell();
                }
            });
            fileColumn.setOnEditCommit(event -> {
                YamlEntry entry = event.getRowValue();
                entry.setValue(fileName, event.getNewValue());
            });

            tableView.getColumns().add(fileColumn);
        }
        adjustColumnWidths();
    }

    private void saveFiles() {
        Yaml yaml = new Yaml();

        for (File file : files) {
            Map<String, Object> data = new HashMap<>();
            for (YamlEntry entry : tableView.getItems()) {
                data.put(entry.getKey(), entry.getValue(file.getName()));
            }

            try (Writer writer = new FileWriter(file)) {
                yaml.dump(data, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void adjustColumnWidths() {
        tableView.getColumns().forEach(column -> {
            double maxWidth = computeMaxColumnWidth((TableColumn<YamlEntry, String>) column);
            column.setPrefWidth(maxWidth);
        });
    }

    private double computeMaxColumnWidth(TableColumn<YamlEntry, String> column) {
        double maxWidth = 100; // Minimum width
        double maxAllowedWidth = 300; // Maximum allowed width
        Text helper = new Text();
        for (YamlEntry item : tableView.getItems()) {
            String cellText = item.getValue(column.getText());
            helper.setText(cellText);
            helper.setFont(new TextArea().getFont()); // Assuming same font as TextArea
            double textWidth = helper.getLayoutBounds().getWidth();
            maxWidth = Math.max(maxWidth, textWidth);
        }
        return Math.min(maxWidth + 20, maxAllowedWidth); // Adding padding and ensuring max width
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class YamlEntry {
    private final StringProperty key;
    private final Map<String, StringProperty> values;

    static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public YamlEntry(String key) {
        this.key = new SimpleStringProperty(key);
        this.values = new HashMap<>();
    }

    public String getKey() {
        return key.get();
    }

    public void setKey(String key) {
        this.key.set(key);
    }

    public StringProperty keyProperty() {
        return key;
    }

    public String getValue(String fileName) {
        return values.getOrDefault(fileName, new SimpleStringProperty("")).get();
    }

    public void setValue(String fileName, Object value) {
        values.put(fileName, new SimpleStringProperty(formatValue(value)));
    }

    public StringProperty valueProperty(String fileName) {
        return values.computeIfAbsent(fileName, k -> new SimpleStringProperty(""));
    }

    private String formatValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                e.printStackTrace();
                return value.toString();
            }
        }
        return value.toString();
    }
}

class TextAreaTableCell extends TableCell<YamlEntry, String> {
    private final TextArea textArea = new TextArea();

    public TextAreaTableCell() {
        textArea.setWrapText(true);
        textArea.setEditable(true);
        textArea.setMinHeight(Region.USE_PREF_SIZE);
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            adjustTextAreaHeight();
        });
        this.setGraphic(textArea);
        this.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        textArea.prefWidthProperty().bind(this.widthProperty());
    }

    private void adjustTextAreaHeight() {
        textArea.setPrefHeight(Region.USE_COMPUTED_SIZE);
        Platform.runLater(() -> {
            double textHeight = computeTextHeight(textArea);
            textArea.setPrefHeight(textHeight + 10);  // Add some padding
            this.setPrefHeight(textHeight + 10);
        });
    }

    private double computeTextHeight(TextArea textArea) {
        Text text = new Text(textArea.getText());
        text.setFont(textArea.getFont());
        text.setWrappingWidth(textArea.getWidth());
        return text.getLayoutBounds().getHeight();
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            textArea.setText(item);
            adjustTextAreaHeight();
            setText(null);
            setGraphic(textArea);
        }
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (getItem() != null) {
            textArea.setText(getItem());
            adjustTextAreaHeight();
            setText(null);
            setGraphic(textArea);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem());
        setGraphic(null);
    }

    @Override
    public void commitEdit(String newValue) {
        super.commitEdit(newValue);
        ((YamlEntry) getTableRow().getItem()).setValue(getTableColumn().getText(), newValue);
    }
}
