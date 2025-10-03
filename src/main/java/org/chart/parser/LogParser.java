package org.chart.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parser that extracts only storage raw dataset entries from the log files.
 *
 * <p>The parser looks for log blocks that start with a line similar to
 * "[01.05.25 00:30:39,746] RawService.UpdateStorageRaw.DataSet:" followed by one or more
 * lines that contain semicolon separated values written to the database. Each of those
 * database lines represents a single tank.</p>
 */
public final class LogParser {

    private static final Pattern DATASET_HEADER = Pattern.compile(
            "\\[.*?]\\s+RawService\\.UpdateStorageRaw\\.DataSet:.*");

    private static final Pattern DATA_LINE = Pattern.compile("\\d+;.*");

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm:ss", Locale.ROOT);

    /**
     * Parses the provided log file.
     *
     * @param path path to the log file
     * @return list of tank records extracted from the log
     * @throws IOException if reading the file fails
     */
    public List<TankRecord> parse(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        }
    }

    /**
     * Parses log data from the provided reader.
     *
     * @param reader reader with log data
     * @return list of tank records extracted from the log
     * @throws IOException if reading from the reader fails
     */
    public List<TankRecord> parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");

        List<TankRecord> result = new ArrayList<>();
        try (BufferedReader bufferedReader = reader instanceof BufferedReader br ? br : new BufferedReader(reader)) {
            String line;
            boolean inDatasetBlock = false;

            while ((line = bufferedReader.readLine()) != null) {
                if (isDatasetHeader(line)) {
                    inDatasetBlock = true;
                    continue;
                }

                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty()) {
                    inDatasetBlock = false;
                    continue;
                }

                if (trimmedLine.startsWith("[")) {
                    inDatasetBlock = false;
                }

                if (!inDatasetBlock) {
                    continue;
                }

                TankRecord record = parseDataLine(trimmedLine);
                if (record != null) {
                    result.add(record);
                }
            }
        }

        return result;
    }

    private static boolean isDatasetHeader(String line) {
        return DATASET_HEADER.matcher(line.trim()).matches();
    }

    private static TankRecord parseDataLine(String line) {
        if (line.isEmpty() || !DATA_LINE.matcher(line).matches()) {
            return null;
        }

        String[] tokens = line.split(";");
        if (tokens.length < 5) {
            return null;
        }

        try {
            long tankId = Long.parseLong(tokens[0].trim());
            LocalDateTime timestamp = LocalDateTime.parse(tokens[1].trim(), TIMESTAMP_FORMATTER);
            BigDecimal levelRaw = parseBigDecimal(tokens[4]);

            return new TankRecord(tankId, timestamp, levelRaw);
        } catch (NumberFormatException | DateTimeParseException ex) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String value) {
        String normalized = value.trim().replace(',', '.');
        return new BigDecimal(normalized);
    }
}
