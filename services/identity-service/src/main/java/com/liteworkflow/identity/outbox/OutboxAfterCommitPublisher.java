package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.config.IdentityProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OutboxAfterCommitPublisher {

    private final OutboxDispatchService dispatchService;
    private final IdentityProperties properties;

    public OutboxAfterCommitPublisher(OutboxDispatchService dispatchService, IdentityProperties properties) {
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
