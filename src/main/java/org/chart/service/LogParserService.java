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

        byte[] fileBytes = Files.readAllBytes(file);
        Charset charset = detectCharset(fileBytes);

        List<ParsedRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(fileBytes), charset))) {
            String line;
            boolean insideDataset = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.contains(DATASET_MARKER)) {
                    insideDataset = true;
                    continue;
                }

                if (!insideDataset) {
                    continue;
                }

                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith("[") && !trimmed.contains(";")) {
                    insideDataset = false;
                    continue;
                }

                if (!isLikelyDataRecord(trimmed)) {
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

        for (int i = 0; i < rows.size(); i++) {
            ParsedRow row = rows.get(i);
            if (row.values().length < 5) {
                continue;
            }

            String tankId = safeValue(row.values(), 0, "Tank " + (i + 1));
            LocalDateTime timestamp = parseTimestamp(requiredValue(row.values(), 1));
            double levelRaw = parseNumber(row.values()[4]);

            DataPoint point = new DataPoint(
                    tankId,
                    createRecordId(tankId, timestamp, i + 1),
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

    private static String requiredValue(String[] values, int index) {
        if (index >= 0 && index < values.length) {
            String value = values[index];
            if (!value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("Отсутствует обязательное значение в колонке " + (index + 1));
    }

    private static boolean isLikelyDataRecord(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length < 5) {
            return false;
        }
        String tankId = parts[0].trim();
        if (tankId.isEmpty()) {
            return false;
        }
        for (int i = 0; i < tankId.length(); i++) {
            char ch = tankId.charAt(i);
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private static String createRecordId(String tankId, LocalDateTime timestamp, int index) {
        return tankId + "-" + timestamp + "-" + index;
    }

    private record ParsedRow(String[] values, String rawLine) {
    }
}
