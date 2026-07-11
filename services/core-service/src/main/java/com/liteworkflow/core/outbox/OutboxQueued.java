package com.liteworkflow.core.outbox;

import java.util.UUID;

public record OutboxQueued(UUID outboxEventId) {
}
