package com.liteworkflow.ai.dto.stream;

import java.util.UUID;

public record ErrorEventData(
        UUID requestId,
        String code,
        String message,
        boolean retryable) implements AiStreamEventData {
}
