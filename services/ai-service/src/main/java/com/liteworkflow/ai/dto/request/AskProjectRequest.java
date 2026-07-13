package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AskProjectRequest(
        @NotNull UUID workspaceId,
        @NotBlank @Size(max = 10_000) String question) {
}
