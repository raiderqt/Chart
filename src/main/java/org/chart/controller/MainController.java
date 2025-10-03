package org.chart.controller;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.chart.chart.ChartFactory;
import org.chart.chart.ZoomManager;
import org.chart.model.DataModel;
import org.chart.model.TankData;
import org.chart.service.LogParserService;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.SignStyle;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composes the user interface and orchestrates the interactions.
 */
public class MainController {

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter();

    private final LogParserService parserService;
    private final ChartFactory chartFactory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "log-parser");
        thread.setDaemon(true);
        return thread;
    });

    private final BorderPane root = new BorderPane();
    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox chartsContainer = new VBox(16);
    private final Label statusLabel = new Label("Данные не загружены");
    private final Button loadButton = new Button("Загрузить лог");
    private final Button applyFilterButton = new Button("Применить фильтр");
    private final Button resetFilterButton = new Button("Сбросить фильтр");
    private final DatePicker fromDatePicker = new DatePicker();
    private final TextField fromTimeField = new TextField();
    private final DatePicker toDatePicker = new DatePicker();
    private final TextField toTimeField = new TextField();
    private final TextField minLevelField = new TextField();
    private final TextField maxLevelField = new TextField();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    private DataModel originalModel;
    private DataModel filteredModel;

    public MainController(LogParserService parserService, ChartFactory chartFactory) {
        this.parserService = parserService;
        this.chartFactory = chartFactory;
        configureLayout();
        attachListeners();
    }

    public Pane getView() {
        return root;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void configureLayout() {
        root.setPrefSize(1280, 800);

        ToolBar toolBar = buildToolbar();
        root.setTop(toolBar);

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setContent(chartsContainer);
        chartsContainer.setPadding(new Insets(16));
        root.setCenter(scrollPane);

        BorderPane.setMargin(statusLabel, new Insets(4, 8, 4, 8));
        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 10, 6, 10));
        statusBar.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #dcdcdc; -fx-border-width: 1 0 0 0;");
        root.setBottom(statusBar);

        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(60, 60);
        StackPane overlay = new StackPane(scrollPane, progressIndicator);
        StackPane.setAlignment(progressIndicator, Pos.CENTER);
        root.setCenter(overlay);
    }

    private ToolBar buildToolbar() {
        loadButton.setDefaultButton(true);
        fromTimeField.setPromptText("HH:mm[:ss]");
        toTimeField.setPromptText("HH:mm[:ss]");
        minLevelField.setPromptText("Мин LevelRaw");
        maxLevelField.setPromptText("Макс LevelRaw");

        ToolBar toolBar = new ToolBar();
        toolBar.getItems().addAll(
                loadButton,
                new Separator(),
                new Label("От:"),
                fromDatePicker,
                fromTimeField,
                new Label("До:"),
                toDatePicker,
                toTimeField,
                new Separator(),
                new Label("LevelRaw:"),
                minLevelField,
                new Label("-"),
                maxLevelField,
                applyFilterButton,
                resetFilterButton
        );
        toolBar.setPadding(new Insets(6));
        toolBar.setStyle("-fx-background-color: #fafafa;");
        return toolBar;
    }

    private void attachListeners() {
        loadButton.setOnAction(e -> chooseAndLoadFile());
        applyFilterButton.setOnAction(e -> applyFilters());
        resetFilterButton.setOnAction(e -> resetFilters());

        fromTimeField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                applyFilters();
            }
        });
        toTimeField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                applyFilters();
            }
        });
    }

    private void chooseAndLoadFile() {
        Window window = root.getScene() == null ? null : root.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбор лог-файла");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Log/CSV", "*.log", "*.txt", "*.csv"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            loadData(file.toPath());
        }
    }

    private void loadData(Path file) {
        toggleLoading(true);
        Task<DataModel> task = new Task<>() {
            @Override
            protected DataModel call() throws Exception {
                updateMessage("Чтение файла...");
                DataModel model = parserService.parse(file);
                updateMessage("Готово");
                return model;
            }
        };

        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            originalModel = task.getValue();
            filteredModel = originalModel;
            clearFilterControls();
            updateCharts(filteredModel);
            updateStatusBar();
            toggleLoading(false);
        });
        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            toggleLoading(false);
            Throwable ex = task.getException();
            statusLabel.setText("Ошибка загрузки");
            showError("Не удалось загрузить данные", ex);
        });
        executor.submit(task);
    }

    private void updateCharts(DataModel model) {
        chartsContainer.getChildren().clear();
        if (model == null || model.getTanks().isEmpty()) {
            Label emptyLabel = new Label("Нет данных для отображения");
            emptyLabel.setStyle("-fx-text-fill: #666666;");
            chartsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (TankData tank : model.getTanks()) {
            chartsContainer.getChildren().add(buildTankPane(tank));
        }
    }

    private TitledPane buildTankPane(TankData tankData) {
        LineChart<Number, Number> chart = chartFactory.createChart(tankData);
        chart.setMinHeight(320);

        ZoomManager zoomManager = new ZoomManager(chart);
        zoomManager.resetZoom();

        Button resetZoom = new Button("Reset zoom");
        resetZoom.setOnAction(e -> zoomManager.resetZoom());

        Button exportButton = new Button("Export PNG");
        exportButton.setOnAction(e -> exportChart(chart, tankData.getTankId()));

        Label countLabel = new Label("Записей: " + tankData.size());

        ToolBar chartToolbar = new ToolBar(resetZoom, exportButton, new Separator(), countLabel);
        chartToolbar.setPadding(new Insets(6));

        BorderPane pane = new BorderPane(chart);
        pane.setTop(chartToolbar);

        TitledPane titledPane = new TitledPane("Tank " + tankData.getTankId(), pane);
        titledPane.setExpanded(true);
        return titledPane;
    }

    private void exportChart(LineChart<Number, Number> chart, String tankId) {
        WritableImageSnapshot snapshot = new WritableImageSnapshot(chart);
        Optional<File> target = snapshot.promptForTarget(getWindow(), "tank-" + tankId + ".png");
        target.ifPresent(file -> {
            try {
                snapshot.writePng(file.toPath());
                statusLabel.setText("Сохранено в " + file.getAbsolutePath());
            } catch (IOException ex) {
                showError("Не удалось сохранить изображение", ex);
            }
        });
    }

    private void applyFilters() {
        if (originalModel == null) {
            return;
        }
        try {
            LocalDateTime from = buildDateTime(fromDatePicker.getValue(), fromTimeField.getText());
            LocalDateTime to = buildDateTime(toDatePicker.getValue(), toTimeField.getText());
            Double minLevel = parseDouble(minLevelField.getText());
            Double maxLevel = parseDouble(maxLevelField.getText());
            filteredModel = originalModel.filter(from, to, minLevel, maxLevel);
            updateCharts(filteredModel);
            updateStatusBar();
        } catch (IllegalArgumentException ex) {
            showError("Неверные параметры фильтра", ex);
        }
    }

    private void resetFilters() {
        if (originalModel == null) {
            return;
        }
        clearFilterControls();
        filteredModel = originalModel;
        updateCharts(filteredModel);
        updateStatusBar();
    }

    private void toggleLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        loadButton.setDisable(loading);
        applyFilterButton.setDisable(loading);
        resetFilterButton.setDisable(loading);
    }

    private void updateStatusBar() {
        if (filteredModel == null) {
            statusLabel.setText("Данные не загружены");
            return;
        }
        StringBuilder builder = new StringBuilder();
        filteredModel.getSource().ifPresent(path -> builder.append(path.getFileName()).append(" "));
        builder.append("Резервуаров: ").append(filteredModel.getTankCount());
        builder.append(", записей: ").append(filteredModel.getTotalRecords());
        if (originalModel != null && filteredModel != originalModel) {
            builder.append(" (из ").append(originalModel.getTotalRecords()).append(")");
        }
        statusLabel.setText(builder.toString());
    }

    private static LocalDateTime buildDateTime(LocalDate date, String timeText) {
        if (date == null && (timeText == null || timeText.isBlank())) {
            return null;
        }
        LocalDate actualDate = date != null ? date : LocalDate.now();
        LocalTime time;
        if (timeText == null || timeText.isBlank()) {
            time = LocalTime.MIN;
        } else {
            try {
                time = LocalTime.parse(timeText.trim(), TIME_FORMATTER);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Неверный формат времени: " + timeText, ex);
            }
        }
        return LocalDateTime.of(actualDate, time);
    }

    private static Double parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Double.parseDouble(text.replace(',', '.'));
    }

    private void showError(String message, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(message);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }

    private void clearFilterControls() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        fromTimeField.clear();
        toTimeField.clear();
        minLevelField.clear();
        maxLevelField.clear();
    }

    private Window getWindow() {
        Scene scene = root.getScene();
        return scene == null ? null : scene.getWindow();
    }

    /**
     * Helper to snapshot and export charts to PNG.
     */
    private static class WritableImageSnapshot {
        private final LineChart<Number, Number> chart;

        WritableImageSnapshot(LineChart<Number, Number> chart) {
            this.chart = chart;
        }

        Optional<File> promptForTarget(Window window, String defaultName) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Сохранение диаграммы");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
            chooser.setInitialFileName(defaultName);
            return Optional.ofNullable(chooser.showSaveDialog(window));
        }

        void writePng(Path target) throws IOException {
            var image = chart.snapshot(null, null);
            ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", target.toFile());
        }
    }
}
