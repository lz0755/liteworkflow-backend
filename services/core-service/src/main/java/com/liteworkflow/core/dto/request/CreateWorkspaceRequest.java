package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description) {
}
