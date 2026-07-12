package com.liteworkflow.ai.dto.stream;

import java.util.UUID;

public record ContextSourceData(String type, UUID id, String title) {
}
