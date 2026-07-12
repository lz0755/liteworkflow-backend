package com.liteworkflow.infra.export;

import java.time.Instant;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        UUID projectId,
        ExportFormat format,
        ExportJobStatus status,
        String failureCode,
        String fileName,
        String contentType,
        Long sizeBytes,
        Long rowCount,
        Instant createdAt,
        Instant completedAt) {

    static ExportJobResponse from(ExportJob job, ExportFile file) {
        return new ExportJobResponse(
                job.getId(),
                job.getProjectId(),
                job.getFormat(),
                job.getStatus(),
                job.getFailureCode(),
                file == null ? null : file.getFileName(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getSizeBytes(),
                file == null ? null : file.getRowCount(),
                job.getCreatedAt(),
                job.getCompletedAt());
    }
}
