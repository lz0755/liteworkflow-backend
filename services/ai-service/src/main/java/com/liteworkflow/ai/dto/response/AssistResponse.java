package com.liteworkflow.ai.dto.response;

import java.util.UUID;

public record AssistResponse(
        UUID requestId,
        UUID conversationId,
        UUID messageId,
        String suggestion,
        AiUsageResponse usage) {
}
