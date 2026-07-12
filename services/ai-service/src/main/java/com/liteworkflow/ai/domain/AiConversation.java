package com.liteworkflow.ai.domain;

import java.time.Instant;
import java.util.UUID;

public record AiConversation(
        UUID id,
        UUID userId,
        UUID workspaceId,
        UUID projectId,
        String operation,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
