package com.liteworkflow.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IssueExportGeneratorTest {

    @TempDir
    java.nio.file.Path tempDirectory;

    @Test
    void writesLargeCsvInBoundedKeysetBatches() throws Exception {
        int rows = 25_000;
        int batchSize = 137;
        IssueExportBatchReader reader = reader(rows);
        AtomicInteger calls = new AtomicInteger();
        when(reader.read(eq(PROJECT_ID), anyLong(), eq((long) rows), eq(batchSize)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return rows(invocation.getArgument(1), rows, batchSize);
                });
        CoreExportProperties properties = properties(batchSize);
        IssueExportGenerator generator = new IssueExportGenerator(reader, properties);

        GeneratedIssueExport export = generator.generate(UUID.randomUUID(), PROJECT_ID, IssueExportFormat.CSV);
        try {
            assertThat(export.rowCount()).isEqualTo(rows);
            assertThat(export.sizeBytes()).isEqualTo(Files.size(export.path())).isGreaterThan(100_000);
            assertThat(export.sha256Hex()).matches("[0-9a-f]{64}");
            assertThat(calls.get()).isEqualTo((rows + batchSize - 1) / batchSize);
            try (var lines = Files.lines(export.path())) {
                assertThat(lines.count()).isEqualTo(rows + 1L);
            }
        } finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test
    void writesBasicXlsxWithSxssfWithoutCollectingAllRows() throws Exception {
        int rows = 5_000;
        int batchSize = 113;
        IssueExportBatchReader reader = reader(rows);
        when(reader.read(eq(PROJECT_ID), anyLong(), eq((long) rows), eq(batchSize)))
                .thenAnswer(invocation -> rows(invocation.getArgument(1), rows, batchSize));
        IssueExportGenerator generator = new IssueExportGenerator(reader, properties(batchSize));

        GeneratedIssueExport export = generator.generate(UUID.randomUUID(), PROJECT_ID, IssueExportFormat.XLSX);
        try {
            assertThat(export.rowCount()).isEqualTo(rows);
            assertThat(export.sizeBytes()).isGreaterThan(50_000);
            try (var workbook = WorkbookFactory.create(export.path().toFile())) {
                assertThat(workbook.getSheet("Issues").getLastRowNum()).isEqualTo(rows);
            }
        } finally {
            Files.deleteIfExists(export.path());
        }
    }

    private IssueExportBatchReader reader(int rows) {
        IssueExportBatchReader reader = mock(IssueExportBatchReader.class);
        when(reader.highWatermark(PROJECT_ID)).thenReturn((long) rows);
        return reader;
    }

    private CoreExportProperties properties(int batchSize) {
        CoreExportProperties properties = new CoreExportProperties();
        properties.setBatchSize(batchSize);
        properties.setTempDirectory(tempDirectory);
        return properties;
    }

    private List<IssueExportRow> rows(long cursor, int highWatermark, int batchSize) {
        long end = Math.min(highWatermark, cursor + batchSize);
        List<IssueExportRow> values = new ArrayList<>();
        for (long number = cursor + 1; number <= end; number++) {
            UUID userId = new UUID(0, number);
            values.add(new IssueExportRow(
                    new UUID(1, number),
                    number,
                    number == 1 ? "=unsafe" : "Issue " + number,
                    "Description " + number,
                    "Open",
                    List.of(userId.toString()),
                    List.of("backend"),
                    userId,
                    userId,
                    Instant.parse("2026-07-12T00:00:00Z"),
                    Instant.parse("2026-07-12T00:00:00Z")));
        }
        return values;
    }

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
}
