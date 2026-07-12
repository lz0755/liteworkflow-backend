package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SummarizeIssueRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID projectId,
        @Size(max = 240) String title,
        @Size(max = 12000) String description,
        @Size(max = 20000) String activityDigest) {
}
