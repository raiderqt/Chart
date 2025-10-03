package org.chart.chart;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Provides scroll and drag-to-zoom capabilities for {@link LineChart} instances.
 */
public class ZoomManager {

    private final LineChart<Number, Number> chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;
    private final Rectangle selection = new Rectangle();
    private Region plotArea;
    private double anchorX;
    private double anchorY;

    public ZoomManager(LineChart<Number, Number> chart) {
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        configureSelectionRectangle();
        installHandlers();
    }

    public void resetZoom() {
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        Platform.runLater(this::storeCurrentBounds);
    }

    public void storeCurrentBounds() {
        if (!xAxis.isAutoRanging()) {
            return;
        }
        double lowerX = xAxis.getLowerBound();
        double upperX = xAxis.getUpperBound();
        double lowerY = yAxis.getLowerBound();
        double upperY = yAxis.getUpperBound();
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        xAxis.setLowerBound(lowerX);
        xAxis.setUpperBound(upperX);
        yAxis.setLowerBound(lowerY);
        yAxis.setUpperBound(upperY);
    }

    private void configureSelectionRectangle() {
        selection.setManaged(false);
        selection.setFill(Color.color(0, 0, 1, 0.15));
        selection.setStroke(Color.DODGERBLUE);
        selection.setStrokeWidth(1.0);
        selection.setVisible(false);
        selection.setMouseTransparent(true);
    }

    private void installHandlers() {
        Platform.runLater(() -> {
            plotArea = (Region) chart.lookup(".chart-plot-background");
            if (plotArea == null) {
                return;
            }
            addSelectionOverlay();
            plotArea.setOnScroll(this::handleScroll);
            plotArea.setOnMousePressed(this::handleMousePressed);
            plotArea.setOnMouseDragged(this::handleMouseDragged);
            plotArea.setOnMouseReleased(this::handleMouseReleased);
        });
    }

    private void addSelectionOverlay() {
        if (plotArea == null) {
            return;
        }
        Parent parent = plotArea.getParent();
        if (parent instanceof Pane pane && !pane.getChildren().contains(selection)) {
            pane.getChildren().add(selection);
            selection.toFront();
        }
    }

    private void handleScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }
        double zoomFactor = event.getDeltaY() > 0 ? 0.9 : 1.1;
        zoomAround(event.getX(), event.getY(), zoomFactor);
        event.consume();
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        anchorX = event.getX();
        anchorY = event.getY();
        selection.setX(anchorX);
        selection.setY(anchorY);
        selection.setWidth(0);
        selection.setHeight(0);
        selection.setVisible(true);
        event.consume();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!selection.isVisible()) {
            return;
        }
        double x = Math.min(event.getX(), anchorX);
        double y = Math.min(event.getY(), anchorY);
        double width = Math.abs(event.getX() - anchorX);
        double height = Math.abs(event.getY() - anchorY);
        selection.setX(x);
        selection.setY(y);
        selection.setWidth(width);
        selection.setHeight(height);
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!selection.isVisible()) {
            return;
        }
        selection.setVisible(false);
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (selection.getWidth() < 5 || selection.getHeight() < 5) {
            return;
        }
        if (plotArea == null) {
            return;
        }
        Bounds plotBounds = plotArea.getLayoutBounds();
        double plotWidth = plotBounds.getWidth();
        double plotHeight = plotBounds.getHeight();
        double minX = clamp(selection.getX(), 0, plotWidth);
        double maxX = clamp(selection.getX() + selection.getWidth(), 0, plotWidth);
        double minY = clamp(selection.getY(), 0, plotHeight);
        double maxY = clamp(selection.getY() + selection.getHeight(), 0, plotHeight);
        applyZoom(minX, maxX, minY, maxY);
        event.consume();
    }

    private void zoomAround(double mouseX, double mouseY, double factor) {
        if (plotArea == null) {
            return;
        }
        Bounds plotBounds = plotArea.getLayoutBounds();
        double plotWidth = plotBounds.getWidth();
        double plotHeight = plotBounds.getHeight();
        double x = clamp(mouseX, 0, plotWidth);
        double y = clamp(mouseY, 0, plotHeight);

        double xValue = xAxis.getValueForDisplay(x).doubleValue();
        double yValue = yAxis.getValueForDisplay(y).doubleValue();

        double lowerX = xAxis.getLowerBound();
        double upperX = xAxis.getUpperBound();
        double lowerY = yAxis.getLowerBound();
        double upperY = yAxis.getUpperBound();

        double newLowerX = xValue - (xValue - lowerX) * factor;
        double newUpperX = xValue + (upperX - xValue) * factor;
        double newLowerY = yValue - (yValue - lowerY) * factor;
        double newUpperY = yValue + (upperY - yValue) * factor;

        applyBounds(newLowerX, newUpperX, newLowerY, newUpperY);
    }

    private void applyZoom(double minX, double maxX, double minY, double maxY) {
        double lowerX = xAxis.getValueForDisplay(minX).doubleValue();
        double upperX = xAxis.getValueForDisplay(maxX).doubleValue();
        double upperY = yAxis.getValueForDisplay(minY).doubleValue();
        double lowerY = yAxis.getValueForDisplay(maxY).doubleValue();
        applyBounds(lowerX, upperX, lowerY, upperY);
    }

    private void applyBounds(double lowerX, double upperX, double lowerY, double upperY) {
        if (Double.isNaN(lowerX) || Double.isNaN(upperX) || Double.isNaN(lowerY) || Double.isNaN(upperY)) {
            return;
        }
        if (upperX - lowerX == 0 || upperY - lowerY == 0) {
            return;
        }
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        xAxis.setLowerBound(Math.min(lowerX, upperX));
        xAxis.setUpperBound(Math.max(lowerX, upperX));
        yAxis.setLowerBound(Math.min(lowerY, upperY));
        yAxis.setUpperBound(Math.max(lowerY, upperY));
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
