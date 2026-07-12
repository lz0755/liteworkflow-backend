package com.liteworkflow.core.export;

import java.util.UUID;

public record IssueExportFailedPayload(
        UUID jobId,
        UUID projectId,
        String failureCode) {
}
