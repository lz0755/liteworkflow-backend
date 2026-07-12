package com.liteworkflow.ai.dto.response;

import java.util.UUID;

public record StructuredSuggestionResponse<T>(
        UUID requestId,
        UUID conversationId,
        T suggestion,
        AiUsageResponse usage) {
}
