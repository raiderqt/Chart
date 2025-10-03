package org.chart.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class LogParserTest {

    @Test
    void parsesOnlyDatasetEntries() throws IOException {
        String log = """
                [01.05.25 00:30:10,111] Some.Other.Service: ignored line
                [01.05.25 00:30:39,746] RawService.UpdateStorageRaw.DataSet:
                877000000002265;01.05.2025 0:30:39;8507,6580;0,8315;14,5;132,2;0;ru_2025.02.25.32047;524288;
                877000000002266;01.05.2025 0:30:22;11760,0620;0,735;15;136,7;0;ru_2025.02.25.32047;524288;
                [01.05.25 00:31:00,000] RawService.UpdateStorageRaw.DataSet:
                877000000002267;01.05.2025 0:31:00;5019,6560;0,7346;15;88,4;0;ru_2025.02.25.32047;524288;
                [01.05.25 00:32:00,000] Another.Service: should be ignored
                """;

        LogParser parser = new LogParser();
        List<TankRecord> records = parser.parse(new StringReader(log));

        assertEquals(3, records.size());

        assertEquals(new TankRecord(877000000002265L,
                        LocalDateTime.of(2025, 5, 1, 0, 30, 39),
                        new BigDecimal("14.5")),
                records.get(0));
        assertEquals(new TankRecord(877000000002266L,
                        LocalDateTime.of(2025, 5, 1, 0, 30, 22),
                        new BigDecimal("15")),
                records.get(1));
        assertEquals(new TankRecord(877000000002267L,
                        LocalDateTime.of(2025, 5, 1, 0, 31),
                        new BigDecimal("15")),
                records.get(2));
    }

    @Test
    void ignoresMalformedDataLines() throws IOException {
        String log = """
                [01.05.25 00:30:39,746] RawService.UpdateStorageRaw.DataSet:
                not-a-valid-line
                877000000002265;bad-date;8507,6580;0,8315;14,5;132,2;0;ru_2025.02.25.32047;524288;
                877000000002266;01.05.2025 0:30:22;11760,0620;0,735;15;136,7;0;ru_2025.02.25.32047;524288;
                """;

        LogParser parser = new LogParser();
        List<TankRecord> records = parser.parse(new StringReader(log));

        assertIterableEquals(List.of(new TankRecord(877000000002266L,
                        LocalDateTime.of(2025, 5, 1, 0, 30, 22),
                        new BigDecimal("15"))),
                records);
    }
}
