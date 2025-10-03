package org.chart.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;

/**
 * Holds all measurements for a particular tank.
 */
public class TankData {

    private final String tankId;
    private final ObservableList<DataPoint> points = FXCollections.observableArrayList();

    public TankData(String tankId) {
        this.tankId = Objects.requireNonNull(tankId, "tankId");
    }

    public String getTankId() {
        return tankId;
    }

    public ObservableList<DataPoint> getPoints() {
        return FXCollections.unmodifiableObservableList(points);
    }

    public void addPoint(DataPoint point) {
        points.add(point);
    }

    public void addAll(List<DataPoint> newPoints) {
        points.addAll(newPoints);
    }

    public int size() {
        return points.size();
    }
}
