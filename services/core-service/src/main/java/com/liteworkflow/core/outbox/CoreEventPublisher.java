package com.liteworkflow.core.outbox;

import com.liteworkflow.core.domain.LocalOutboxEvent;

@FunctionalInterface
public interface CoreEventPublisher {

    void publish(LocalOutboxEvent event);
}
