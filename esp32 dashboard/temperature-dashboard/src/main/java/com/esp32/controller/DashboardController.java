package com.esp32.controller;

import com.esp32.model.TemperatureReading;
import com.esp32.service.CsvExportService;
import com.esp32.service.SerialCommunicationService;
import com.esp32.service.TemperatureService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DashboardController {
    private static final String BG = "#081426";
    private static final String CARD = "#10243b";
    private static final String CYAN = "#35d7e8";
    private static final String MUTED = "#91a8bd";
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    private final TemperatureService temperatureService = new TemperatureService();
    private final CsvExportService csvExportService = new CsvExportService();
    private final SerialCommunicationService serialService = new SerialCommunicationService();
    private final ComboBox<String> portSelector = new ComboBox<>();
    private final ComboBox<Integer> baudSelector = new ComboBox<>(FXCollections.observableArrayList(9600, 19200, 38400, 57600, 115200));
    private final Label connectionStatus = new Label("Disconnected");
    private final Label sensorStatus = new Label("Waiting for readings");
    private final Label temperatureLabel = new Label("--.- °C");
    private final Label temperatureStatus = new Label("No sensor data");
    private final Label minimumLabel = new Label("-- °C");
    private final Label maximumLabel = new Label("-- °C");
    private final Label readingsLabel = new Label("0");
    private final Label updatedLabel = new Label("Last updated: never");
    private final TextField thresholdField = new TextField("30.0");
    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    private Stage stage;
    private double threshold = 30.0;

    public void show(Stage stage) {
        this.stage = stage;
        serialService.setReadingListener(reading -> Platform.runLater(() -> handleReading(reading)));
        serialService.setStatusListener(status -> Platform.runLater(() -> updateConnectionStatus(status)));
        serialService.setErrorListener(error -> Platform.runLater(() -> showError("Serial connection", error.getMessage())));
        stage.setOnCloseRequest(event -> serialService.close());

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24, 28, 32, 28));
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setTop(createHeader());
        root.setCenter(createContent());

        Scene scene = new Scene(root, 1220, 820);
        stage.setTitle("ESP32 IoT Temperature Dashboard");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        refreshPorts();
        stage.show();
    }

    private VBox createHeader() {
        Label title = new Label("ESP32 IoT Dashboard");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + CYAN + ";");
        Label subtitle = new Label("DS18B20 Temperature Monitoring System");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: " + MUTED + ";");
        connectionStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #ffb454; -fx-font-weight: bold;");
        sensorStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + MUTED + ";");
        VBox header = new VBox(6, title, subtitle, connectionStatus, sensorStatus);
        header.setAlignment(javafx.geometry.Pos.CENTER);
        header.setPadding(new Insets(0, 0, 22, 0));
        return header;
    }

    private VBox createContent() {
        VBox content = new VBox(18);
        HBox statistics = new HBox(10,
                createCurrentCard(),
                createStatisticCard("MINIMUM TODAY", minimumLabel, "#62e6a7"),
                createStatisticCard("MAXIMUM TODAY", maximumLabel, "#ffb454"),
                createStatisticCard("TOTAL READINGS", readingsLabel, CYAN));
        for (javafx.scene.Node node : statistics.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        VBox chartCard = createChartCard();
        chartCard.setPrefWidth(700);
        chartCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        VBox.setVgrow(chartCard, Priority.ALWAYS);

        VBox controls = new VBox(14, createAlertCard(), createDataCard(), createConnectionCard());
        controls.setPrefWidth(320);
        controls.setMinWidth(300);
        controls.setMaxWidth(360);

        HBox mainRow = new HBox(18, chartCard, controls);
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        VBox.setVgrow(mainRow, Priority.ALWAYS);

        content.getChildren().addAll(statistics, mainRow);
        content.setMaxWidth(1160);
        content.setFillWidth(true);
        return content;
    }

    private VBox createConnectionCard() {
        portSelector.setPromptText("Select COM port");
        portSelector.setEditable(true);
        baudSelector.setValue(115200);
        Button refresh = button("Refresh", false);
        refresh.setOnAction(event -> refreshPorts());
        Button connect = button("Connect", true);
        connect.setOnAction(event -> connect());
        Button disconnect = button("Disconnect", false);
        disconnect.setOnAction(event -> serialService.disconnect());
        HBox portRow = new HBox(8, portSelector, refresh);
        HBox.setHgrow(portSelector, Priority.ALWAYS);
        HBox controls = new HBox(8, fieldLabel("Baud rate"), baudSelector, connect, disconnect);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox card = card("ESP32 CONNECTION", portRow, controls);
        return card;
    }

    private VBox createCurrentCard() {
        temperatureLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + CYAN + ";");
        temperatureStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: " + MUTED + ";");
        VBox card = card("CURRENT TEMPERATURE", temperatureLabel, temperatureStatus);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        return card;
    }

    private VBox createAlertCard() {
        thresholdField.setMaxWidth(Double.MAX_VALUE);
        Button save = button("Save limit", true);
        save.setOnAction(event -> saveThreshold());
        Label hint = new Label("You will be alerted when a reading exceeds this value.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: " + MUTED + ";");
        return card("TEMPERATURE ALERT SETTINGS", fieldLabel("Set your maximum allowed temperature."),
                new HBox(8, thresholdField, save), hint);
    }

    private VBox createStatisticCard(String title, Label value, String color) {
        value.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return card(title, value);
    }

    private VBox createChartCard() {
        xAxis.setLabel("Time");
        yAxis.setLabel("Temperature (°C)");
        xAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number object) {
                return java.time.Instant.ofEpochSecond(object.longValue()).atZone(ZoneId.systemDefault())
                        .toLocalTime().toString().substring(0, 5);
            }
            @Override public Number fromString(String string) { return 0; }
        });
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(false);
        chart.setStyle("-fx-background-color: transparent; -fx-padding: 8;");
        series.setName("Temperature");
        chart.getData().add(series);
        Label description = new Label("Temperature changes over time");
        description.setStyle("-fx-font-size: 10px; -fx-text-fill: " + MUTED + ";");
        return card("LIVE TEMPERATURE GRAPH", description, chart);
    }

    private VBox createDataCard() {
        Label description = new Label("Readings are stored in your session. Daily minimum and maximum reset when the data changes.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 10px; -fx-text-fill: " + MUTED + ";");
        Button export = button("Export CSV", true);
        export.setOnAction(event -> exportCsv());
        Button clear = button("Clear history", false);
        clear.setOnAction(event -> clearHistory());
        return card("TEMPERATURE DATA", description, new HBox(8, export, clear), updatedLabel);
    }

    private VBox card(String title, javafx.scene.Node... nodes) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + MUTED + ";");
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 12;");
        box.getChildren().add(heading);
        box.getChildren().addAll(nodes);
        return box;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + MUTED + ";");
        return label;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(text);
        String color = primary ? "#4c8edb" : "#29445d";
        String textColor = "white";
        button.setStyle("-fx-background-color: " + color + "; -fx-text-fill: " + textColor
                + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 9 14;");
        return button;
    }

    private void refreshPorts() {
        String currentPort = portSelector.getEditor().getText().trim();
        List<String> ports = new ArrayList<>(SerialCommunicationService.availablePorts().stream()
                .map(port -> {
                    String description = port.getDescriptivePortName();
                    return description == null || description.isBlank()
                            ? port.getSystemPortName()
                            : port.getSystemPortName() + " - " + description;
                })
                .toList());
        if (ports.stream().noneMatch(port -> port.equalsIgnoreCase("COM3")
                || port.startsWith("COM3 - "))) {
            ports.add("COM3");
        }
        portSelector.getItems().setAll(ports);
        if (!portSelector.getItems().isEmpty()) {
            if (currentPort.isBlank()) {
                portSelector.getSelectionModel().selectFirst();
            } else {
                portSelector.getEditor().setText(currentPort);
            }
        } else {
            portSelector.getEditor().setText(currentPort);
            portSelector.setPromptText("Type COM port (for example COM3)");
        }
    }

    private void connect() {
        String selectedPort = portSelector.getEditor().getText().trim();
        if (selectedPort.isBlank()) {
            showError("Connection", "Select or enter a COM port, for example COM3.");
            return;
        }
        String portName = selectedPort.split(" - ", 2)[0].trim();
        serialService.connect(portName, baudSelector.getValue());
    }

    private void handleReading(TemperatureReading reading) {
        temperatureService.add(reading);
        series.getData().add(new XYChart.Data<>(reading.timestamp().atZone(ZoneId.systemDefault()).toEpochSecond(), reading.temperatureCelsius()));
        temperatureLabel.setText(String.format("%.1f °C", reading.temperatureCelsius()));
        temperatureStatus.setText(reading.temperatureCelsius() > threshold ? "HIGH TEMPERATURE ALERT" : "Normal temperature");
        temperatureStatus.setStyle("-fx-font-size: 15px; -fx-text-fill: " + (reading.temperatureCelsius() > threshold ? "#ff6b6b" : "#62e6a7") + ";");
        sensorStatus.setText("Sensor data received");
        minimumLabel.setText(String.format("%.1f °C", temperatureService.minimum().orElse(0)));
        maximumLabel.setText(String.format("%.1f °C", temperatureService.maximum().orElse(0)));
        readingsLabel.setText(Integer.toString(temperatureService.readings().size()));
        updatedLabel.setText("Last updated: " + reading.timestamp().format(UPDATED));
    }

    private void updateConnectionStatus(String status) {
        connectionStatus.setText(status);
        connectionStatus.setStyle("-fx-text-fill: " + (status.startsWith("Connected") ? "#62e6a7" : "#ffb454") + "; -fx-font-weight: bold;");
    }

    private void saveThreshold() {
        try {
            double value = Double.parseDouble(thresholdField.getText().trim());
            if (!Double.isFinite(value) || value < -100 || value > 150) {
                throw new NumberFormatException();
            }
            threshold = value;
            thresholdField.setText(String.format("%.1f", threshold));
        } catch (NumberFormatException exception) {
            showError("Temperature alert", "Enter a valid threshold between -100 and 150 °C.");
        }
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export temperature readings");
        chooser.setInitialFileName("temperature-readings.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            csvExportService.export(file.toPath(), temperatureService.readings());
        } catch (IOException exception) {
            showError("CSV export", exception.getMessage());
        }
    }

    private void clearHistory() {
        temperatureService.clear();
        series.getData().clear();
        temperatureLabel.setText("--.- °C");
        temperatureStatus.setText("No sensor data");
        sensorStatus.setText("Waiting for readings");
        minimumLabel.setText("-- °C");
        maximumLabel.setText("-- °C");
        readingsLabel.setText("0");
        updatedLabel.setText("Last updated: never");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "An unexpected error occurred." : message);
        alert.show();
    }

}
