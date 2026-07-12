package com.liteworkflow.core.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IssueExportGenerator {

    private final IssueExportBatchReader reader;
    private final CoreExportProperties properties;

    public IssueExportGenerator(IssueExportBatchReader reader, CoreExportProperties properties) {
        this.reader = reader;
        this.properties = properties;
    }

    public GeneratedIssueExport generate(
            UUID jobId,
            UUID projectId,
            IssueExportFormat format) {
        Path path = null;
        try {
            Files.createDirectories(properties.getTempDirectory());
            path = Files.createTempFile(
                    properties.getTempDirectory(), "issue-export-" + jobId + "-", "." + format.extension());
            long highWatermark = reader.highWatermark(projectId);
            long cursor = 0;
            long rowCount = 0;
            try (IssueExportDocumentWriter writer = writer(format, path)) {
                while (cursor < highWatermark) {
                    List<IssueExportRow> batch = reader.read(
                            projectId, cursor, highWatermark, properties.getBatchSize());
                    if (batch.isEmpty()) break;
                    for (IssueExportRow row : batch) writer.write(row);
                    long nextCursor = batch.getLast().issueNumber();
                    if (nextCursor <= cursor) {
                        throw new IllegalStateException("Issue export cursor did not advance");
                    }
                    cursor = nextCursor;
                    rowCount += batch.size();
                }
            }
            return new GeneratedIssueExport(
                    path, format, Files.size(path), sha256(path), rowCount);
        } catch (Exception exception) {
            deleteQuietly(path);
            throw new IssueExportGenerationException("Issue export generation failed", exception);
        }
    }

    private IssueExportDocumentWriter writer(IssueExportFormat format, Path path) throws IOException {
        return switch (format) {
            case CSV -> new CsvIssueExportWriter(path);
            case XLSX -> new XlsxIssueExportWriter(path);
        };
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later temp-directory cleanup can remove an otherwise harmless closed file.
        }
    }
}
