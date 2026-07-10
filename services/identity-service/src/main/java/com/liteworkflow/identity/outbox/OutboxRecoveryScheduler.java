package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.config.IdentityProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRecoveryScheduler {

    private final OutboxDispatchService dispatchService;
    private final IdentityProperties properties;

    public OutboxRecoveryScheduler(OutboxDispatchService dispatchService, IdentityProperties properties) {
        this.dispatchService = dispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${liteworkflow.identity.outbox.retry-delay:10s}")
    public void recover() {
        if (properties.getOutbox().isSchedulingEnabled()) {
            dispatchService.recoverPending();
        }
    }
}
