package com.liteworkflow.core.export;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreExportAmqpConfiguration {

    public static final String EXCHANGE = "export.exchange";
    public static final String REQUESTED = "export.issues.requested";
    public static final String COMPLETED = "export.issues.completed";
    public static final String FAILED = "export.issues.failed";
    public static final String REQUEST_QUEUE = "core.issue-exports";
    public static final String REQUEST_DLQ = "core.issue-exports.dlq";
    public static final String REQUEST_DLX = "core.issue-exports.dlx";

    @Bean
    TopicExchange coreExportExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange exportRequestDeadLetterExchange() {
        return new DirectExchange(REQUEST_DLX, true, false);
    }

    @Bean
    Queue exportRequestQueue() {
        return new Queue(
                REQUEST_QUEUE,
                true,
                false,
                false,
                Map.of("x-dead-letter-exchange", REQUEST_DLX,
                        "x-dead-letter-routing-key", REQUEST_DLQ));
    }

    @Bean
    Queue exportRequestDeadLetterQueue() {
        return new Queue(REQUEST_DLQ, true);
    }

    @Bean
    Binding exportRequestBinding(
            @Qualifier("exportRequestQueue") Queue queue,
            @Qualifier("coreExportExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(REQUESTED);
    }

    @Bean
    Binding exportRequestDeadLetterBinding(
            @Qualifier("exportRequestDeadLetterQueue") Queue queue,
            @Qualifier("exportRequestDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(REQUEST_DLQ);
    }

    @Bean(name = "exportRabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory exportRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            ExportRequestRecoverer recoverer,
            CoreExportProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        CoreExportProperties.Consumer retry = properties.getConsumer();
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(retry.getMaxAttempts())
                .backOffOptions(
                        retry.getInitialInterval().toMillis(),
                        retry.getMultiplier(),
                        retry.getMaxInterval().toMillis())
                .recoverer(recoverer)
                .build());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
