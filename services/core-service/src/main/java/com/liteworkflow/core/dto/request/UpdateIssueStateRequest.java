package com.liteworkflow.core.dto.request;

import com.liteworkflow.core.domain.IssueStateCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateIssueStateRequest(
        @Size(max = 80) String name,
        IssueStateCategory category,
        @Min(0) Integer position,
        Boolean defaultState) {
}
