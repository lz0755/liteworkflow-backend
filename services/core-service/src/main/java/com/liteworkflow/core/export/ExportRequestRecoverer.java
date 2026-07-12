package com.liteworkflow.core.export;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

@Component
public class ExportRequestRecoverer implements MessageRecoverer {

    private final ObjectMapper json;
    private final ExportOutcomeRecorder outcomes;
    private final RabbitTemplate rabbit;

    public ExportRequestRecoverer(ObjectMapper json, ExportOutcomeRecorder outcomes, RabbitTemplate rabbit) {
        this.json = json;
        this.outcomes = outcomes;
        this.rabbit = rabbit;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        EventEnvelope<IssueExportRequestedPayload> envelope = parse(message);
        if (envelope != null) {
            outcomes.recordFailed(envelope, "EXPORT_PROCESSING_FAILED");
        }
        rabbit.send(
                CoreExportAmqpConfiguration.REQUEST_DLX,
                CoreExportAmqpConfiguration.REQUEST_DLQ,
                message);
    }

    private EventEnvelope<IssueExportRequestedPayload> parse(Message message) {
        try {
            JavaType type = json.getTypeFactory().constructParametricType(
                    EventEnvelope.class, IssueExportRequestedPayload.class);
            return json.readValue(message.getBody(), type);
        } catch (Exception ignored) {
            return null;
        }
    }
}
