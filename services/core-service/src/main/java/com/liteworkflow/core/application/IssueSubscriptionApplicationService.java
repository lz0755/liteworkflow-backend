package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.IssueSubscriber;
import com.liteworkflow.core.domain.IssueSubscriberId;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.dto.response.IssueSubscriptionResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueSubscriberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueSubscriptionApplicationService {

    private static final String SUBSCRIBER_AGGREGATE = "ISSUE_SUBSCRIBER";

    private final PermissionService permissionService;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final IssueSubscriberRepository subscriberRepository;
    private final ActivityOutboxService activityOutboxService;
    private final Clock clock;

    public IssueSubscriptionApplicationService(
            PermissionService permissionService,
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            IssueSubscriberRepository subscriberRepository,
            ActivityOutboxService activityOutboxService,
            Clock clock) {
        this.permissionService = permissionService;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.subscriberRepository = subscriberRepository;
        this.activityOutboxService = activityOutboxService;
        this.clock = clock;
    }

    @Transactional
    public IssueSubscriptionResponse subscribe(UUID actorId, UUID issueId) {
        // Serializes duplicate subscribe/unsubscribe requests for the same issue.
        Issue issue = requireIssueForUpdate(issueId);
        permissionService.requireProjectMember(issue.getProjectId(), actorId);
        IssueSubscriberId id = new IssueSubscriberId(issueId, actorId);
        IssueSubscriber existing = subscriberRepository.findById(id).orElse(null);
        if (existing != null) {
            return new IssueSubscriptionResponse(issueId, actorId, true, existing.getCreatedAt());
        }

        Instant now = clock.instant();
        subscriberRepository.save(new IssueSubscriber(issueId, actorId, actorId, now));
        record("issue.subscriber.added", issue, actorId, now);
        return new IssueSubscriptionResponse(issueId, actorId, true, now);
    }

    @Transactional
    public IssueSubscriptionResponse unsubscribe(UUID actorId, UUID issueId) {
        Issue issue = requireIssueForUpdate(issueId);
        permissionService.requireProjectMember(issue.getProjectId(), actorId);
        IssueSubscriberId id = new IssueSubscriberId(issueId, actorId);
        if (!subscriberRepository.existsById(id)) {
            return new IssueSubscriptionResponse(issueId, actorId, false, null);
        }
        subscriberRepository.deleteById(id);
        Instant now = clock.instant();
        record("issue.subscriber.removed", issue, actorId, now);
        return new IssueSubscriptionResponse(issueId, actorId, false, null);
    }

    @Transactional(readOnly = true)
    public IssueSubscriptionResponse get(UUID actorId, UUID issueId) {
        Issue issue = requireIssue(issueId);
        permissionService.requireProjectMember(issue.getProjectId(), actorId);
        return subscriberRepository.findById(new IssueSubscriberId(issueId, actorId))
                .map(value -> new IssueSubscriptionResponse(issueId, actorId, true, value.getCreatedAt()))
                .orElseGet(() -> new IssueSubscriptionResponse(issueId, actorId, false, null));
    }

    private void record(String eventType, Issue issue, UUID actorId, Instant now) {
        Project project = projectRepository.findActiveById(issue.getProjectId())
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        activityOutboxService.recordProjectChange(
                eventType,
                project.getWorkspaceId(),
                project.getId(),
                SUBSCRIBER_AGGREGATE,
                issue.getId(),
                actorId,
                Map.of("issueId", issue.getId(), "subscriberUserId", actorId, "changedAt", now));
    }

    private Issue requireIssue(UUID issueId) {
        return issueRepository.findActiveById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }

    private Issue requireIssueForUpdate(UUID issueId) {
        return issueRepository.findActiveByIdForUpdate(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }
}
