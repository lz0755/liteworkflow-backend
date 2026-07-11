package com.liteworkflow.core.outbox;

import com.liteworkflow.core.config.CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "liteworkflow.core.outbox",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRecoveryScheduler {

    private final OutboxDispatchService dispatchService;
    private final CoreProperties properties;

    public OutboxRecoveryScheduler(OutboxDispatchService dispatchService, CoreProperties properties) {
        this.dispatchService = dispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${liteworkflow.core.outbox.retry-delay:10s}")
    public void recover() {
        if (properties.getOutbox().isSchedulingEnabled()) {
            dispatchService.recoverPending();
        }
    }
}
