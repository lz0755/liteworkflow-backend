package com.liteworkflow.ai.domain;

public record AiTokenUsage(int inputTokens, int outputTokens, int totalTokens) {

    public static final AiTokenUsage ZERO = new AiTokenUsage(0, 0, 0);

    public AiTokenUsage {
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        totalTokens = Math.max(Math.max(0, totalTokens), inputTokens + outputTokens);
    }
}
