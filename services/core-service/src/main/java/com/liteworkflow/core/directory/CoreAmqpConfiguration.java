package com.liteworkflow.core.directory;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreAmqpConfiguration {

    public static final String IDENTITY_EXCHANGE = "identity.event.exchange";
    public static final String IDENTITY_USER_QUEUE = "core.identity-user-directory";
    public static final String IDENTITY_USER_DLQ = "core.identity-user-directory.dlq";
    public static final String IDENTITY_DLX = "core.identity-user-directory.dlx";

    @Bean
    TopicExchange coreIdentityEventExchange() {
        return new TopicExchange(IDENTITY_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange coreIdentityDeadLetterExchange() {
        return new DirectExchange(IDENTITY_DLX, true, false);
    }

    @Bean
    Queue coreIdentityUserQueue() {
        return new Queue(
                IDENTITY_USER_QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", IDENTITY_DLX,
                        "x-dead-letter-routing-key", IDENTITY_USER_DLQ));
    }

    @Bean
    Queue coreIdentityUserDeadLetterQueue() {
        return new Queue(IDENTITY_USER_DLQ, true);
    }

    @Bean
    Binding coreIdentityUserBinding(Queue coreIdentityUserQueue, TopicExchange coreIdentityEventExchange) {
        return BindingBuilder.bind(coreIdentityUserQueue)
                .to(coreIdentityEventExchange)
                .with("identity.user.*");
    }

    @Bean
    Binding coreIdentityUserDeadLetterBinding(
            Queue coreIdentityUserDeadLetterQueue, DirectExchange coreIdentityDeadLetterExchange) {
        return BindingBuilder.bind(coreIdentityUserDeadLetterQueue)
                .to(coreIdentityDeadLetterExchange)
                .with(IDENTITY_USER_DLQ);
    }
}
