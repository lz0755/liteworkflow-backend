package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GenerateIssuesRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID projectId,
        @NotBlank @Size(max = 2000) String goal,
        @Size(max = 12000) String context,
        @Min(1) @Max(10) Integer count) {

    public int requestedCount() {
        return count == null ? 3 : count;
    }
}
