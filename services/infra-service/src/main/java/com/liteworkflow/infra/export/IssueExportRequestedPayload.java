package com.liteworkflow.infra.export;

import java.util.UUID;

public record IssueExportRequestedPayload(
        UUID jobId,
        UUID projectId,
        UUID requestedBy,
        ExportFormat format) {
}
