package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.domain.LocalOutboxEvent;

public interface IdentityEventPublisher {
    void publish(LocalOutboxEvent event);
}
