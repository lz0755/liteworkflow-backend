package com.liteworkflow.ai.dto.stream;

/** Marker for the only data shapes allowed on the public AI SSE contract. */
public sealed interface AiStreamEventData permits
        ContextEventData,
        DeltaEventData,
        UsageEventData,
        DoneEventData,
        ErrorEventData {
}
