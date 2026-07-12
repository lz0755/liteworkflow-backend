package com.liteworkflow.infra.notification;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationAmqpConfiguration {

    public static final String WORK_EXCHANGE = "work.event.exchange";
    public static final String QUEUE = "infra.collaboration-notifications";
    public static final String DLQ = "infra.collaboration-notifications.dlq";
    public static final String DLX = "infra.collaboration-notifications.dlx";

    @Bean
    TopicExchange infraWorkEventExchange() {
        return new TopicExchange(WORK_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue collaborationNotificationQueue() {
        return new Queue(
                QUEUE,
                true,
                false,
                false,
                Map.of("x-dead-letter-exchange", DLX, "x-dead-letter-routing-key", DLQ));
    }

    @Bean
    Queue collaborationNotificationDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    Binding commentMentionNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("comment.mentioned");
    }

    @Bean
    Binding workspaceMemberAddedNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("workspace.member.added");
    }

    @Bean
    Binding projectMemberAddedNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("project.member.added");
    }

    @Bean
    Binding issueCreatedNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("issue.created");
    }

    @Bean
    Binding issueAssigneesNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("issue.assignees.changed");
    }

    @Bean
    Binding issueStateNotificationBinding(
            Queue collaborationNotificationQueue, TopicExchange infraWorkEventExchange) {
        return BindingBuilder.bind(collaborationNotificationQueue)
                .to(infraWorkEventExchange)
                .with("issue.state.changed");
    }

    @Bean
    Binding collaborationNotificationDeadLetterBinding(
            Queue collaborationNotificationDeadLetterQueue, DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(collaborationNotificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(DLQ);
    }
}
