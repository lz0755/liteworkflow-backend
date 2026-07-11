package com.liteworkflow.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.core.domain.Activity;
import com.liteworkflow.core.domain.LocalOutboxEvent;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityOutboxService {

    public static final String WORK_EXCHANGE = "work.event.exchange";
    public static final String WORKSPACE_MEMBER_AGGREGATE = "WORKSPACE_MEMBER";
    public static final String PROJECT_MEMBER_AGGREGATE = "PROJECT_MEMBER";

    private final ActivityRepository activityRepository;
    private final LocalOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public ActivityOutboxService(
            ActivityRepository activityRepository,
            LocalOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.activityRepository = activityRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWorkspaceMemberChange(
            String eventType,
            UUID workspaceId,
            UUID memberId,
            UUID actorId,
            WorkspaceMemberEventPayload payload) {
        Instant now = clock.instant();
        var activityPayload = objectMapper.valueToTree(payload);
        activityRepository.save(new Activity(
                UUID.randomUUID(),
                workspaceId,
                actorId,
                eventType,
                WORKSPACE_MEMBER_AGGREGATE,
                memberId,
                activityPayload,
                now));

        UUID eventId = UUID.randomUUID();
        EventEnvelope<WorkspaceMemberEventPayload> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                1,
                now,
                new EventScope(workspaceId, null, actorId),
                memberId,
                payload,
                Map.of());
        outboxRepository.save(new LocalOutboxEvent(
                eventId,
                eventType,
                WORK_EXCHANGE,
                eventType,
                WORKSPACE_MEMBER_AGGREGATE,
                memberId,
                workspaceId,
                null,
                actorId,
                objectMapper.valueToTree(envelope),
                now));
        applicationEventPublisher.publishEvent(new OutboxQueued(eventId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProjectMemberChange(
            String eventType,
            UUID workspaceId,
            UUID projectId,
            UUID memberId,
            UUID actorId,
            ProjectMemberEventPayload payload) {
        Instant now = clock.instant();
        var activityPayload = objectMapper.valueToTree(payload);
        activityRepository.save(new Activity(
                UUID.randomUUID(),
                workspaceId,
                actorId,
                eventType,
                PROJECT_MEMBER_AGGREGATE,
                memberId,
                activityPayload,
                now));

        UUID eventId = UUID.randomUUID();
        EventEnvelope<ProjectMemberEventPayload> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                1,
                now,
                new EventScope(workspaceId, projectId, actorId),
                memberId,
                payload,
                Map.of());
        outboxRepository.save(new LocalOutboxEvent(
                eventId,
                eventType,
                WORK_EXCHANGE,
                eventType,
                PROJECT_MEMBER_AGGREGATE,
                memberId,
                workspaceId,
                projectId,
                actorId,
                objectMapper.valueToTree(envelope),
                now));
        applicationEventPublisher.publishEvent(new OutboxQueued(eventId));
    }

}
