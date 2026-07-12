package com.liteworkflow.ai.application;

import com.liteworkflow.ai.domain.AiTokenUsage;

/** Provider-neutral streaming data. No provider SDK or wire chunk crosses this boundary. */
public record AiProviderStreamChunk(
        String text,
        AiTokenUsage usage,
        String model,
        String finishReason) {
}
