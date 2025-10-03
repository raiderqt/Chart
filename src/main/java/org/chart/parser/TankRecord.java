package org.chart.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single tank entry extracted from the log.
 */
public record TankRecord(long tankId, LocalDateTime timestamp, BigDecimal levelRaw) {
}
