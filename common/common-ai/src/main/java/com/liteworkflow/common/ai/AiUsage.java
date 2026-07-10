package com.liteworkflow.common.ai;

public record AiUsage(long inputTokens, long outputTokens, long totalTokens) {

    public AiUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("totalTokens must cover input and output tokens");
        }
    }
}
