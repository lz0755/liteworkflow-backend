package com.liteworkflow.core.export;

import java.util.UUID;

public record IssueExportRequestedPayload(
        UUID jobId,
        UUID projectId,
        UUID requestedBy,
        IssueExportFormat format) {
}
