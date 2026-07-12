package com.liteworkflow.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;

class NotificationAmqpConfigurationTest {

    @Test
    void durableQueueRoutesExhaustedFiniteRetriesToItsDlq() {
        NotificationAmqpConfiguration configuration = new NotificationAmqpConfiguration();
        var queue = configuration.collaborationNotificationQueue();
        var deadLetterQueue = configuration.collaborationNotificationDeadLetterQueue();

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", NotificationAmqpConfiguration.DLX)
                .containsEntry("x-dead-letter-routing-key", NotificationAmqpConfiguration.DLQ);
        assertThat(deadLetterQueue.isDurable()).isTrue();
        assertThat(configuration.collaborationNotificationDeadLetterBinding(
                        deadLetterQueue,
                        new DirectExchange(NotificationAmqpConfiguration.DLX)))
                .extracting(binding -> binding.getRoutingKey())
                .isEqualTo(NotificationAmqpConfiguration.DLQ);
    }

    @Test
    void queueBindsEveryM9NotificationSourceEvent() {
        NotificationAmqpConfiguration configuration = new NotificationAmqpConfiguration();
        var queue = configuration.collaborationNotificationQueue();
        var exchange = new TopicExchange(NotificationAmqpConfiguration.WORK_EXCHANGE);

        assertThat(Set.of(
                        configuration.workspaceMemberAddedNotificationBinding(queue, exchange).getRoutingKey(),
                        configuration.projectMemberAddedNotificationBinding(queue, exchange).getRoutingKey(),
                        configuration.issueCreatedNotificationBinding(queue, exchange).getRoutingKey(),
                        configuration.issueAssigneesNotificationBinding(queue, exchange).getRoutingKey(),
                        configuration.issueStateNotificationBinding(queue, exchange).getRoutingKey(),
                        configuration.commentMentionNotificationBinding(queue, exchange).getRoutingKey()))
                .containsExactlyInAnyOrder(
                        "workspace.member.added",
                        "project.member.added",
                        "issue.created",
                        "issue.assignees.changed",
                        "issue.state.changed",
                        "comment.mentioned");
    }

    @Test
    void consumerDoesNotSwallowFailuresSoContainerRetryAndDlqCanRun() {
        NotificationProjectionService projection = mock(NotificationProjectionService.class);
        @SuppressWarnings("unchecked")
        EventEnvelope<JsonNode> event = mock(EventEnvelope.class);
        when(projection.consume(event)).thenThrow(new IllegalStateException("projection failed"));

        assertThatThrownBy(() -> new NotificationEventConsumer(projection).consume(event))
                .isInstanceOf(IllegalStateException.class);
    }
}
