package org.chart.service;

import org.chart.model.DataModel;
import org.chart.model.DataPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses the log file and extracts the CSV dataset described in the specification.
 */
public class LogParserService {

    private static final String DATASET_MARKER = "RawService.UpdateStorageRaw.DataSet:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm[:ss]");

    public DataModel parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");

        List<ParsedRow> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean insideDataset = false;

            while ((line = reader.readLine()) != null) {
                if (!insideDataset) {
                    if (line.contains(DATASET_MARKER)) {
                        insideDataset = true;
                    }
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    insideDataset = false;
                    continue;
                }

                if (!trimmed.contains(";")) {
                    insideDataset = false;
                    continue;
                }

                String[] values = Arrays.stream(trimmed.split(";", -1))
                        .map(String::trim)
                        .toArray(String[]::new);
                rows.add(new ParsedRow(values, trimmed));
            }
        }

        DataModel model = new DataModel();
        model.setSource(file);

        if (rows.isEmpty()) {
            return model;
        }

        int tankIdIndex = determineTankIdColumn(rows);
        for (int i = 0; i < rows.size(); i++) {
            ParsedRow row = rows.get(i);
            if (row.values().length < 5) {
                continue;
            }

            String recordId = safeValue(row.values(), 0, "record-" + (i + 1));
            String tankId = safeValue(row.values(), tankIdIndex, "");
            if (tankId.isBlank()) {
                tankId = "Tank 1";
            }
            LocalDateTime timestamp = parseTimestamp(safeValue(row.values(), 1, null));
            double levelRaw = parseNumber(row.values()[4]);

            DataPoint point = new DataPoint(
                    tankId,
                    recordId,
                    timestamp,
                    levelRaw,
                    List.copyOf(Arrays.asList(row.values())),
                    row.rawLine());
            model.addPoint(point);
        }

        return model;
    }

    private static int determineTankIdColumn(List<ParsedRow> rows) {
        int maxColumns = rows.stream().mapToInt(row -> row.values().length).max().orElse(0);
        int totalRows = rows.size();

        int bestIndex = -1;
        int bestDistinct = Integer.MAX_VALUE;

        for (int column = 0; column < maxColumns; column++) {
            Set<String> distinct = new HashSet<>();
            boolean hasValue = false;
            for (ParsedRow row : rows) {
                if (row.values().length <= column) {
                    continue;
                }
                String value = row.values()[column];
                if (!value.isBlank()) {
                    hasValue = true;
                    distinct.add(value);
                    if (totalRows > 10 && distinct.size() > totalRows / 2) {
                        break;
                    }
                }
            }
            if (!hasValue) {
                continue;
            }
            int distinctCount = distinct.size();
            if (distinctCount == 0) {
                continue;
            }
            if (distinctCount == 1 && bestIndex == -1) {
                bestIndex = column;
                bestDistinct = distinctCount;
            } else if (distinctCount > 1 && distinctCount < totalRows && distinctCount < bestDistinct) {
                bestIndex = column;
                bestDistinct = distinctCount;
            }
        }

        return bestIndex >= 0 ? bestIndex : 0;
    }

    private static LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Пустое значение времени");
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Не удалось распарсить дату/время: " + value, ex);
        }
    }

    private static double parseNumber(String value) {
        if (value == null || value.isEmpty()) {
            return Double.NaN;
        }
        String normalized = value.replace(',', '.');
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Не удалось распарсить число: " + value, ex);
        }
    }

    private static String safeValue(String[] values, int index, String defaultValue) {
        if (index >= 0 && index < values.length) {
            String value = values[index];
            if (!value.isBlank()) {
                return value;
            }
        }
        return defaultValue == null ? "" : defaultValue;
    }

    private record ParsedRow(String[] values, String rawLine) {
    }
}
