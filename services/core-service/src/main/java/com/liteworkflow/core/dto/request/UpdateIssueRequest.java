package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateIssueRequest(
        @Size(max = 240) String title,
        @Size(max = 20000) String description) {
}
