package com.liteworkflow.core.domain;

import java.io.Serializable;
import java.util.UUID;

public record IssueMentionId(UUID commentId, UUID userId) implements Serializable {
}
