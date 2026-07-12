package com.liteworkflow.infra.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.infra.notification.ConsumedNotificationEventRepository;
import com.liteworkflow.infra.notification.NotificationProjectionService;
import com.liteworkflow.infra.notification.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.slf4j.MDC;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class EmailDeliveryServiceTest {

    @Autowired private EmailDeliveryService deliveryService;
    @Autowired private NotificationProjectionService projectionService;
    @Autowired private EmailOutboxRepository outboxRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsumedNotificationEventRepository consumedEventRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NotificationContactClient contactClient;
    @MockBean private EmailSender emailSender;

    @BeforeEach
    void resetState() {
        MDC.clear();
        reset(contactClient, emailSender);
        outboxRepository.deleteAll();
        emailLogRepository.deleteAll();
        notificationRepository.deleteAll();
        consumedEventRepository.deleteAll();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sendsARecipientResolvedBuiltInEmailAndMarksTheOutboxSent() {
        UUID recipientId = UUID.randomUUID();
        createMentionEmail(recipientId, "private comment body");
        EmailOutboxJob job = outboxRepository.findAll().getFirst();
        when(contactClient.get(recipientId))
                .thenReturn(new NotificationContact(recipientId, "recipient@example.test"));

        deliveryService.deliver(job.getId());

        ArgumentCaptor<RenderedEmail> email = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("recipient@example.test"), email.capture());
        assertThat(email.getValue().subject()).isEqualTo("You were mentioned in a comment");
        assertThat(email.getValue().textBody()).doesNotContain("private comment body", "recipient@example.test");
        assertThat(email.getValue().htmlBody()).contains("<a href=").doesNotContain("private comment body");
        assertThat(outboxRepository.findById(job.getId())).get()
                .extracting(EmailOutboxJob::getStatus)
                .isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(emailLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
            assertThat(log.getAttemptCount()).isOne();
        });
    }

    @Test
    void deliveryUsesFiniteRetriesAndNeverLogsMailSensitiveContent(CapturedOutput output) {
        UUID recipientId = UUID.randomUUID();
        String sensitiveAddress = "sensitive-mailbox@example.test";
        String sensitiveBody = "confidential comment body";
        createMentionEmail(recipientId, sensitiveBody);
        EmailOutboxJob job = outboxRepository.findAll().getFirst();
        when(contactClient.get(recipientId))
                .thenReturn(new NotificationContact(recipientId, sensitiveAddress));
        doThrow(new IllegalStateException("provider echoed " + sensitiveAddress + " " + sensitiveBody))
                .when(emailSender)
                .send(org.mockito.ArgumentMatchers.eq(sensitiveAddress), org.mockito.ArgumentMatchers.any());

        deliveryService.deliver(job.getId());
        deliveryService.deliver(job.getId());

        assertThat(outboxRepository.findById(job.getId())).get().satisfies(stored -> {
            assertThat(stored.getStatus()).isEqualTo(EmailDeliveryStatus.DEAD);
            assertThat(stored.getRetryCount()).isEqualTo(2);
        });
        assertThat(emailLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.DEAD);
            assertThat(log.getAttemptCount()).isEqualTo(2);
            assertThat(log.getLastErrorCode()).isEqualTo("DELIVERY_FAILED");
        });
        assertThat(output.getAll()).doesNotContain(sensitiveAddress, sensitiveBody, "provider echoed");
    }

    @Test
    void persistsRabbitTraceAndScopesEveryScheduledDeliveryWithoutLeakingMdc() {
        UUID recipientId = UUID.randomUUID();
        MDC.put(TraceConstants.TRACE_ID, "rabbit-notification-trace");
        UUID sourceEventId = createMentionEmail(recipientId, "ignored");
        EmailOutboxJob job = outboxRepository.findAll().getFirst();
        assertThat(job.getTraceId()).isEqualTo("rabbit-notification-trace");
        MDC.clear();
        MDC.put("preservedSchedulerKey", "preserved");
        when(contactClient.get(recipientId))
                .thenReturn(new NotificationContact(recipientId, "recipient@example.test"));
        AtomicReference<Map<String, String>> deliveryMdc = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
                    deliveryMdc.set(MDC.getCopyOfContextMap());
                    return null;
                })
                .when(emailSender)
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        deliveryService.deliver(job.getId());

        assertThat(deliveryMdc.get())
                .containsEntry(TraceConstants.TRACE_ID, "rabbit-notification-trace")
                .containsEntry(TraceConstants.EVENT_ID, sourceEventId.toString())
                .containsEntry(TraceConstants.USER_ID, recipientId.toString());
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isNull();
        assertThat(MDC.get(TraceConstants.EVENT_ID)).isNull();
        assertThat(MDC.get(TraceConstants.USER_ID)).isNull();
        assertThat(MDC.get("preservedSchedulerKey")).isEqualTo("preserved");
    }

    private UUID createMentionEmail(UUID recipientId, String ignoredBody) {
        UUID eventId = UUID.randomUUID();
        projectionService.consume(new EventEnvelope<>(
                eventId,
                "comment.mentioned",
                1,
                Instant.now(),
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(),
                objectMapper.valueToTree(Map.of(
                        "recipientUserIds", List.of(recipientId),
                        "body", ignoredBody)),
                Map.of()));
        return eventId;
    }
}
