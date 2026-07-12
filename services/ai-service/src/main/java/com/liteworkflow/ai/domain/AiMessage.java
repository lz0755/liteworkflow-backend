package com.liteworkflow.ai.domain;

import java.time.Instant;
import java.util.UUID;

public record AiMessage(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        int tokenCount,
        Instant createdAt) {
}
