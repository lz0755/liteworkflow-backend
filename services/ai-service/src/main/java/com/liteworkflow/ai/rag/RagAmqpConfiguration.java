package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.config.RagProperties;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagAmqpConfiguration {

    public static final String RAG_EXCHANGE = "rag.exchange";
    public static final String WORK_EXCHANGE = "work.event.exchange";
    public static final String INDEX_QUEUE = "ai.rag-index";
    public static final String INDEX_DLX = "ai.rag-index.dlx";
    public static final String INDEX_DLQ = "ai.rag-index.dlq";

    @Bean
    TopicExchange aiRagExchange() {
        return new TopicExchange(RAG_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange aiRagWorkExchange() {
        return new TopicExchange(WORK_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ragIndexDeadLetterExchange() {
        return new DirectExchange(INDEX_DLX, true, false);
    }

    @Bean
    Queue ragIndexQueue() {
        return new Queue(INDEX_QUEUE, true, false, false,
                Map.of("x-dead-letter-exchange", INDEX_DLX, "x-dead-letter-routing-key", INDEX_DLQ));
    }

    @Bean
    Queue ragIndexDeadLetterQueue() {
        return new Queue(INDEX_DLQ, true);
    }

    @Bean
    Binding ragIssueCreatedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.created");
    }

    @Bean
    Binding ragIssueUpdatedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.updated");
    }

    @Bean
    Binding ragIssueStateBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.state.changed");
    }

    @Bean
    Binding ragIssueAssigneesBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.assignees.changed");
    }

    @Bean
    Binding ragIssueLabelsBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.labels.changed");
    }

    @Bean
    Binding ragIssueDeletedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("issue.deleted");
    }

    @Bean
    Binding ragCommentCreatedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("comment.created");
    }

    @Bean
    Binding ragCommentUpdatedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("comment.updated");
    }

    @Bean
    Binding ragCommentDeletedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagWorkExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("comment.deleted");
    }

    @Bean
    Binding ragDocumentUpsertBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("rag.document.upsert");
    }

    @Bean
    Binding ragDocumentDeletedBinding(
            @Qualifier("ragIndexQueue") Queue queue,
            @Qualifier("aiRagExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("rag.document.deleted");
    }

    @Bean
    Binding ragIndexDeadLetterBinding(
            @Qualifier("ragIndexDeadLetterQueue") Queue queue,
            @Qualifier("ragIndexDeadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(INDEX_DLQ);
    }

    @Bean(name = "ragRabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory ragRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RagProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        long initial = properties.getIndexRetryInitialInterval().toMillis();
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(properties.getIndexMaxAttempts())
                .backOffOptions(initial, 2.0, initial * 4)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
