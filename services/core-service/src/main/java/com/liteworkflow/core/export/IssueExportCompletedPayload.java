package com.liteworkflow.core.export;

import java.util.UUID;

public record IssueExportCompletedPayload(
        UUID jobId,
        UUID projectId,
        IssueExportFormat format,
        String bucket,
        String objectKey,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256Hex,
        long rowCount) {
}
