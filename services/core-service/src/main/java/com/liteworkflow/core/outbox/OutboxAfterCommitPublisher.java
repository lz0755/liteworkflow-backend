package com.liteworkflow.core.outbox;

import com.liteworkflow.core.config.CoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OutboxAfterCommitPublisher {

    private final OutboxDispatchService dispatchService;
    private final CoreProperties properties;

    public OutboxAfterCommitPublisher(OutboxDispatchService dispatchService, CoreProperties properties) {
        this.dispatchService = dispatchService;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OutboxQueued queued) {
        if (properties.getOutbox().isImmediatePublish()) {
            dispatchService.dispatch(queued.outboxEventId());
        }
    }
}
