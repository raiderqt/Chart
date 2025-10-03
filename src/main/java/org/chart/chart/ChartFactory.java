package org.chart.chart;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import org.chart.model.DataPoint;
import org.chart.model.TankData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds fully configured charts for tank data.
 */
public class ChartFactory {

    private static final DateTimeFormatter AXIS_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.getDefault());
    private static final DateTimeFormatter TOOLTIP_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public LineChart<Number, Number> createChart(TankData tankData) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Время");
        xAxis.setAutoRanging(true);
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return formatInstant(object);
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("LevelRaw");
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setTitle("Tank " + tankData.getTankId());
        chart.setPadding(new Insets(10));

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (DataPoint point : tankData.getPoints()) {
            XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(toEpochMillis(point.getTimestamp()), point.getLevelRaw());
            dataPoint.setExtraValue(point);
            series.getData().add(dataPoint);
        }

        chart.getData().add(series);
        attachTooltips(series);

        return chart;
    }

    private void attachTooltips(XYChart.Series<Number, Number> series) {
        for (XYChart.Data<Number, Number> data : series.getData()) {
            DataPoint point = (DataPoint) data.getExtraValue();
            if (point == null) {
                continue;
            }
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tooltip = new Tooltip(buildTooltip(point));
                    Tooltip.install(newNode, tooltip);
                }
            });
            if (data.getNode() != null) {
                Tooltip tooltip = new Tooltip(buildTooltip(point));
                Tooltip.install(data.getNode(), tooltip);
            }
        }
    }

    private static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String formatInstant(Number value) {
        long millis = value.longValue();
        return AXIS_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static String buildTooltip(DataPoint point) {
        StringBuilder builder = new StringBuilder();
        builder.append("Время: ").append(TOOLTIP_FORMATTER.format(point.getTimestamp())).append('\n');
        builder.append("LevelRaw: ").append(String.format(Locale.getDefault(), "%.4f", point.getLevelRaw())).append('\n');
        builder.append("Tank ID: ").append(point.getTankId()).append('\n');
        builder.append("Record ID: ").append(point.getRecordId()).append('\n');
        builder.append("Значения: ").append(String.join("; ", point.getRawValues()));
        return builder.toString();
    }
}
