package com.liteworkflow.core.outbox;

import com.liteworkflow.core.config.CoreProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreOutboxConfiguration {

    @Bean
    TopicExchange workEventExchange() {
        return new TopicExchange(ActivityOutboxService.WORK_EXCHANGE, true, false);
    }

    @Bean
    CoreEventPublisher coreEventPublisher(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider, CoreProperties properties) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            rabbitTemplate.setMandatory(true);
            return new RabbitCoreEventPublisher(
                    rabbitTemplate, properties.getOutbox().getPublisherConfirmTimeout());
        }
        return event -> {
            throw new IllegalStateException("RabbitMQ publisher is unavailable");
        };
    }
}
