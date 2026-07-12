package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record WeeklyReportRequest(
        @NotNull UUID workspaceId,
        @NotNull LocalDate weekStart,
        @NotNull LocalDate weekEnd,
        @Size(max = 30000) String activityDigest) {
}
