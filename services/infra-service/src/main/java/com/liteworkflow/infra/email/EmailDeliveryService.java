package com.liteworkflow.infra.email;

import com.liteworkflow.common.core.trace.MdcScope;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.infra.notification.NotificationProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    private final EmailOutboxRepository outboxRepository;
    private final EmailLogRepository logRepository;
    private final NotificationContactClient contactClient;
    private final EmailSender emailSender;
    private final NotificationProperties properties;
    private final Clock clock;

    public EmailDeliveryService(
            EmailOutboxRepository outboxRepository,
            EmailLogRepository logRepository,
            NotificationContactClient contactClient,
            EmailSender emailSender,
            NotificationProperties properties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.logRepository = logRepository;
        this.contactClient = contactClient;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void deliver(UUID jobId) {
        EmailOutboxJob job = outboxRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || (job.getStatus() != EmailDeliveryStatus.PENDING
                && job.getStatus() != EmailDeliveryStatus.RETRYING)) {
            return;
        }
        try (MdcScope ignored = MdcScope.open(mdcContext(job))) {
            EmailLog emailLog = logRepository.findById(job.getEmailLogId())
                    .orElseThrow(() -> new IllegalStateException("Email log is missing"));
            Instant now = clock.instant();
            try {
                NotificationContact contact = contactClient.get(job.getRecipientUserId());
                BuiltinEmailTemplate template = BuiltinEmailTemplate.valueOf(job.getTemplateCode());
                emailSender.send(contact.email(), template.render(job, properties));
                job.markSent(now);
                emailLog.markSent(now);
            } catch (RuntimeException exception) {
                String errorCode = deliveryErrorCode(exception);
                boolean terminal = job.markFailed(
                        now,
                        properties.getEmail().getMaxAttempts(),
                        properties.getEmail().getRetryDelay());
                emailLog.markFailed(now, errorCode, terminal);
                // Never log the address, subject/body, SMTP response, or exception text.
                log.warn(
                        "Email delivery attempt failed jobId={}, recipientUserId={}, templateCode={}, attempt={}, terminal={}",
                        job.getId(),
                        job.getRecipientUserId(),
                        job.getTemplateCode(),
                        job.getRetryCount(),
                        terminal);
            }
        }
    }

    private Map<String, String> mdcContext(EmailOutboxJob job) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(TraceConstants.TRACE_ID, job.getTraceId());
        context.put(TraceConstants.EVENT_ID, job.getSourceEventId().toString());
        context.put(TraceConstants.USER_ID, job.getRecipientUserId().toString());
        if (job.getWorkspaceId() != null) {
            context.put(TraceConstants.WORKSPACE_ID, job.getWorkspaceId().toString());
        }
        if (job.getProjectId() != null) {
            context.put(TraceConstants.PROJECT_ID, job.getProjectId().toString());
        }
        return context;
    }

    private String deliveryErrorCode(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                ? "INVALID_TEMPLATE"
                : "DELIVERY_FAILED";
    }
}
