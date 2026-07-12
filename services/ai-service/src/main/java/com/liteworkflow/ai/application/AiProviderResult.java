package com.liteworkflow.ai.application;

import com.liteworkflow.ai.domain.AiTokenUsage;

public record AiProviderResult<T>(T value, String rawContent, AiTokenUsage usage, String model) {
}
