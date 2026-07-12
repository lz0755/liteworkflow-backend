package com.liteworkflow.ai.dto.stream;

public record DeltaEventData(String text) implements AiStreamEventData {
}
