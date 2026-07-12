package com.liteworkflow.ai.dto.response;

import com.liteworkflow.ai.domain.AiTokenUsage;

public record AiUsageResponse(int inputTokens, int outputTokens, int totalTokens) {

    public static AiUsageResponse from(AiTokenUsage usage) {
        return new AiUsageResponse(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
}
