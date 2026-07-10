package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.config.IdentityProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityAmqpConfiguration {

    @Bean
    TopicExchange identityEventExchange() {
        return new TopicExchange(IdentityOutboxService.IDENTITY_EXCHANGE, true, false);
    }

    @Bean
    IdentityEventPublisher identityEventPublisher(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider, IdentityProperties properties) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            rabbitTemplate.setMandatory(true);
            return new RabbitIdentityEventPublisher(
                    rabbitTemplate, properties.getOutbox().getPublisherConfirmTimeout());
        }
        // The dispatcher converts this to FAILED and leaves the outbox row recoverable.
        return event -> {
            throw new IllegalStateException("RabbitMQ publisher is unavailable");
        };
    }
}
