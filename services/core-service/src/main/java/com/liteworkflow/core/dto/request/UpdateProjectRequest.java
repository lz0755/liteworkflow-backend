package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 120) String name,
        @Size(max = 2000) String description) {
}
