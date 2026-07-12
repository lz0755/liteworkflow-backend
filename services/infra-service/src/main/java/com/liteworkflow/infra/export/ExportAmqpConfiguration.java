package com.liteworkflow.infra.export;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExportAmqpConfiguration {

    public static final String EXCHANGE = "export.exchange";
    public static final String REQUESTED = "export.issues.requested";
    public static final String COMPLETED = "export.issues.completed";
    public static final String FAILED = "export.issues.failed";
    public static final String STATUS_QUEUE = "infra.issue-export-status";
    public static final String STATUS_DLQ = "infra.issue-export-status.dlq";
    public static final String STATUS_DLX = "infra.issue-export-status.dlx";

    @Bean
    TopicExchange infraExportExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange exportStatusDeadLetterExchange() {
        return new DirectExchange(STATUS_DLX, true, false);
    }

    @Bean
    Queue exportStatusQueue() {
        return new Queue(
                STATUS_QUEUE,
                true,
                false,
                false,
                Map.of("x-dead-letter-exchange", STATUS_DLX,
                        "x-dead-letter-routing-key", STATUS_DLQ));
    }

    @Bean
    Queue exportStatusDeadLetterQueue() {
        return new Queue(STATUS_DLQ, true);
    }

    @Bean
    Binding exportCompletedBinding(
            @Qualifier("exportStatusQueue") Queue queue,
            @Qualifier("infraExportExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(COMPLETED);
    }

    @Bean
    Binding exportFailedBinding(
            @Qualifier("exportStatusQueue") Queue queue,
            @Qualifier("infraExportExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(FAILED);
    }

    @Bean
    Binding exportStatusDeadLetterBinding(
            @Qualifier("exportStatusDeadLetterQueue") Queue queue,
            @Qualifier("exportStatusDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(STATUS_DLQ);
    }
}
