package com.liteworkflow.core.export;

import java.nio.file.Path;

public record GeneratedIssueExport(
        Path path,
        IssueExportFormat format,
        long sizeBytes,
        String sha256Hex,
        long rowCount) {
}
