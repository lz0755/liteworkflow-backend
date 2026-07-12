package com.liteworkflow.ai.dto.stream;

public record DoneEventData(String finishReason) implements AiStreamEventData {
}
