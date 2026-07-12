package com.liteworkflow.ai.dto.response;

import com.liteworkflow.ai.domain.AiMessage;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, String role, String content, int tokenCount, Instant createdAt) {

    public static MessageResponse from(AiMessage message) {
        return new MessageResponse(
                message.id(), message.role(), message.content(), message.tokenCount(), message.createdAt());
    }
}
