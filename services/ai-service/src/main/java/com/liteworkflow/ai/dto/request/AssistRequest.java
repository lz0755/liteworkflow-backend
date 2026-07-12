package com.liteworkflow.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssistRequest(
        UUID conversationId,
        UUID workspaceId,
        UUID projectId,
        @NotBlank @Size(max = 8000) String message) {
}
