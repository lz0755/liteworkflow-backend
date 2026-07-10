package com.liteworkflow.identity.outbox;

import java.util.UUID;

public record OutboxQueued(UUID outboxEventId) {
}
