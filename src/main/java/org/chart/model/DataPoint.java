package org.chart.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single measurement record parsed from the log file.
 */
public class DataPoint {

    private final String tankId;
    private final String recordId;
    private final LocalDateTime timestamp;
    private final double levelRaw;
    private final List<String> rawValues;
    private final String rawLine;

    public DataPoint(String tankId,
                     String recordId,
                     LocalDateTime timestamp,
                     double levelRaw,
                     List<String> rawValues,
                     String rawLine) {
        this.tankId = Objects.requireNonNull(tankId, "tankId");
        this.recordId = Objects.requireNonNull(recordId, "recordId");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.levelRaw = levelRaw;
        this.rawValues = List.copyOf(rawValues);
        this.rawLine = Objects.requireNonNull(rawLine, "rawLine");
    }

    public String getTankId() {
        return tankId;
    }

    public String getRecordId() {
        return recordId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public double getLevelRaw() {
        return levelRaw;
    }

    public List<String> getRawValues() {
        return rawValues;
    }

    public String getRawLine() {
        return rawLine;
    }
}
