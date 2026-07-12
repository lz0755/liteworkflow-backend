package com.liteworkflow.core.export;

import com.liteworkflow.common.core.event.EventEnvelope;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class IssueExportRequestConsumer {

    private final IssueExportProcessor processor;

    public IssueExportRequestConsumer(IssueExportProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(
            queues = CoreExportAmqpConfiguration.REQUEST_QUEUE,
            containerFactory = "exportRabbitListenerContainerFactory")
    public void consume(EventEnvelope<IssueExportRequestedPayload> envelope) {
        processor.process(envelope);
    }
}
