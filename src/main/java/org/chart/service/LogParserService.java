package org.chart.service;

import org.chart.model.DataModel;
import org.chart.model.DataPoint;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.*;

/**
 * Parses the log file and extracts the CSV dataset described in the specification.
 */
public class LogParserService {

    private static final String DATASET_MARKER = "RawService.UpdateStorageRaw.DataSet:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm[:ss]");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .toFormatter();

    public DataModel parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");

        byte[] fileBytes = Files.readAllBytes(file);
        Charset charset = detectCharset(fileBytes);

        List<ParsedRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(fileBytes), charset))) {
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
            LocalDateTime timestamp = parseTimestamp(row);
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

    private static Charset detectCharset(byte[] data) {
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (data.length >= 2) {
            int first = data[0] & 0xFF;
            int second = data[1] & 0xFF;
            if (first == 0xFF && second == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (first == 0xFE && second == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }

        for (Charset candidate : CHARSET_CANDIDATES) {
            CharsetDecoder decoder = candidate.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                decoder.decode(ByteBuffer.wrap(data));
                return candidate;
            } catch (CharacterCodingException ex) {
                // try next
            }
        }

        return StandardCharsets.UTF_8;
    }

    private static final List<Charset> CHARSET_CANDIDATES = createCharsetCandidates();

    private static List<Charset> createCharsetCandidates() {
        List<Charset> candidates = new ArrayList<>();
        candidates.add(StandardCharsets.UTF_8);
        candidates.add(StandardCharsets.UTF_16LE);
        candidates.add(StandardCharsets.UTF_16BE);
        addIfSupported(candidates, "windows-1251");
        addIfSupported(candidates, "koi8-r");
        addIfSupported(candidates, "cp866");
        return List.copyOf(candidates);
    }

    private static void addIfSupported(List<Charset> target, String charsetName) {
        if (Charset.isSupported(charsetName)) {
            target.add(Charset.forName(charsetName));
        }
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

    private static LocalDateTime parseTimestamp(ParsedRow row) {
        String[] values = row.values();
        String timeValue = extract(values, 1);
        if (timeValue == null || timeValue.isBlank()) {
            throw new IllegalArgumentException("Пустое значение времени");
        }

        LocalDateTime direct = tryParseDateTime(timeValue);
        if (direct != null) {
            return direct;
        }

        String dateValue = extract(values, 0);
        LocalDate datePart = tryParseDate(dateValue);
        if (datePart != null) {
            LocalTime timePart = tryParseTime(timeValue);
            if (timePart != null) {
                return LocalDateTime.of(datePart, timePart);
            }
        }

        throw new IllegalArgumentException("Не удалось распарсить дату/время: " + timeValue);
    }

    private static String extract(String[] values, int index) {
        if (index >= 0 && index < values.length) {
            String value = values[index];
            return value == null ? null : value.trim();
        }
        return null;
    }

    private static LocalDateTime tryParseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        String trimmed = value.trim();
        candidates.add(trimmed);
        String normalized = normalizeDateTime(trimmed);
        if (!normalized.equals(trimmed)) {
            candidates.add(normalized);
        }

        for (String candidate : candidates) {
            try {
                return LocalDateTime.parse(candidate, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private static LocalDate tryParseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        String normalized = trimmed.replace('/', '.');
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime tryParseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        String trimmed = value.trim();
        candidates.add(trimmed);
        String normalized = normalizeTime(trimmed);
        if (!normalized.equals(trimmed)) {
            candidates.add(normalized);
        }

        for (String candidate : candidates) {
            try {
                return LocalTime.parse(candidate, TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }

        for (String candidate : candidates) {
            LocalTime fromDecimal = parseDecimalTime(candidate);
            if (fromDecimal != null) {
                return fromDecimal;
            }
        }

        return null;
    }

    private static String normalizeDateTime(String value) {
        int spaceIndex = value.indexOf(' ');
        if (spaceIndex < 0) {
            return normalizeTime(value);
        }
        String datePart = value.substring(0, spaceIndex).replace('/', '.');
        String timePart = normalizeTime(value.substring(spaceIndex + 1));
        return datePart + " " + timePart;
    }

    private static String normalizeTime(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String candidate = trimmed.replace('.', ':');
        if (candidate.contains(",") && !candidate.contains(":")) {
            String[] parts = candidate.split(",", -1);
            if (parts.length == 2 && parts[0].matches("\\d{1,2}") && parts[1].matches("\\d{1,2}")) {
                String minutes = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                candidate = parts[0] + ":" + minutes;
            } else {
                candidate = candidate.replace(',', ':');
            }
        } else {
            candidate = candidate.replace(',', ':');
        }

        long colonCount = candidate.chars().filter(ch -> ch == ':').count();
        if (colonCount == 1) {
            int colonIndex = candidate.indexOf(':');
            if (colonIndex >= 0 && colonIndex < candidate.length() - 1) {
                String minutes = candidate.substring(colonIndex + 1);
                if (minutes.length() == 1) {
                    candidate = candidate.substring(0, colonIndex + 1) + "0" + minutes;
                }
            }
        }

        return candidate;
    }

    private static LocalTime parseDecimalTime(String value) {
        if (value.contains(":")) {
            return null;
        }
        String normalized = value.replace(',', '.');
        try {
            double hours = Double.parseDouble(normalized);
            if (!Double.isFinite(hours)) {
                return null;
            }
            int wholeHours = (int) Math.floor(hours);
            double fractional = hours - wholeHours;
            int totalSeconds = (int) Math.round(fractional * 3600);
            while (totalSeconds >= 3600) {
                wholeHours++;
                totalSeconds -= 3600;
            }
            if (wholeHours >= 24) {
                return null;
            }
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            if (minutes >= 60) {
                minutes -= 60;
                wholeHours++;
                if (wholeHours >= 24) {
                    return null;
                }
            }
            return LocalTime.of(wholeHours, minutes, seconds);
        } catch (NumberFormatException ex) {
            return null;
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
