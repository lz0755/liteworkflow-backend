package com.liteworkflow.ai.dto.stream;

import com.liteworkflow.ai.domain.AiTokenUsage;

public record UsageEventData(int inputTokens, int outputTokens, int totalTokens)
        implements AiStreamEventData {

    public static UsageEventData from(AiTokenUsage usage) {
        return new UsageEventData(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
}
