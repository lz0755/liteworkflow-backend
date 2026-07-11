package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.IssueStateCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIssueStateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull IssueStateCategory category,
        @Min(0) int position,
        boolean defaultState) {
}
