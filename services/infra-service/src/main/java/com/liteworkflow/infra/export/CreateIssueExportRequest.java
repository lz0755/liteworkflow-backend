package com.liteworkflow.infra.export;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateIssueExportRequest(
        @NotNull UUID projectId,
        @NotNull ExportFormat format) {
}
