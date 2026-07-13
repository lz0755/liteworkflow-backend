package com.liteworkflow.ai.dto.response;

import java.util.UUID;

public record ProjectAskSource(
        String sourceType,
        UUID sourceId,
        long sourceVersion,
        int chunkIndex,
        String title,
        double similarity) {
}
