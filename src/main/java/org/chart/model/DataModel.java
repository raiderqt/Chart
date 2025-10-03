package org.chart.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Aggregates data loaded from the log file and exposes convenience filtering.
 */
public class DataModel {

    private final Map<String, TankData> tanks = new LinkedHashMap<>();
    private Path source;
    private int totalRecords;

    public void addPoint(DataPoint point) {
        Objects.requireNonNull(point, "point");
        tanks.computeIfAbsent(point.getTankId(), TankData::new).addPoint(point);
        totalRecords++;
    }

    public void addTankData(TankData tankData) {
        tanks.put(tankData.getTankId(), tankData);
        totalRecords += tankData.size();
    }

    public Collection<TankData> getTanks() {
        return tanks.values();
    }

    public Optional<TankData> getTank(String tankId) {
        return Optional.ofNullable(tanks.get(tankId));
    }

    public int getTankCount() {
        return tanks.size();
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setSource(Path source) {
        this.source = source;
    }

    public Optional<Path> getSource() {
        return Optional.ofNullable(source);
    }

    public DataModel filter(LocalDateTime from,
                            LocalDateTime to,
                            Double minLevel,
                            Double maxLevel) {
        Predicate<DataPoint> predicate = point -> {
            boolean timeOk = (from == null || !point.getTimestamp().isBefore(from))
                    && (to == null || !point.getTimestamp().isAfter(to));
            boolean levelOk = (minLevel == null || point.getLevelRaw() >= minLevel)
                    && (maxLevel == null || point.getLevelRaw() <= maxLevel);
            return timeOk && levelOk;
        };

        DataModel filtered = new DataModel();
        filtered.setSource(source);

        for (TankData tank : tanks.values()) {
            List<DataPoint> filteredPoints = tank.getPoints().stream()
                    .filter(predicate)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!filteredPoints.isEmpty()) {
                TankData copy = new TankData(tank.getTankId());
                copy.addAll(filteredPoints);
                filtered.addTankData(copy);
            }
        }

        return filtered;
    }
}
