package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BreakdownIssueRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID projectId,
        @Size(max = 240) String title,
        @Size(max = 12000) String description,
        @Min(1) @Max(20) Integer maxSubtasks) {

    public int requestedMaxSubtasks() {
        return maxSubtasks == null ? 8 : maxSubtasks;
    }
}
