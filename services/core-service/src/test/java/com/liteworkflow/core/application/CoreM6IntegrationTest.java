package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.IssueLabelStatus;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.OutboxStatus;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.ChangeIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateIssueLabelRequest;
import com.liteworkflow.core.dto.request.CreateIssueRequest;
import com.liteworkflow.core.dto.request.CreateIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.dto.request.UpdateIssueLabelRequest;
import com.liteworkflow.core.dto.request.UpdateIssueRequest;
import com.liteworkflow.core.dto.request.UpdateIssueStateRequest;
import com.liteworkflow.core.dto.response.IssueResponse;
import com.liteworkflow.core.outbox.CoreEventPublisher;
import com.liteworkflow.core.outbox.OutboxDispatchService;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.IssueAssigneeRepository;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import com.liteworkflow.core.repository.ProjectIssueCounterRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class CoreM6IntegrationTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private ProjectMemberApplicationService projectMemberService;
    @Autowired private IssueApplicationService issueService;
    @Autowired private IssueCatalogApplicationService catalogService;
    @Autowired private OutboxDispatchService outboxDispatchService;
    @Autowired private CoreProperties coreProperties;
    @Autowired private IssueRepository issueRepository;
    @Autowired private IssueAssigneeRepository assigneeRepository;
    @Autowired private IssueLabelRelationRepository labelRelationRepository;
    @Autowired private IssueLabelRepository labelRepository;
    @Autowired private ProjectIssueCounterRepository counterRepository;
    @Autowired private IssueStateRepository stateRepository;
    @Autowired private LocalOutboxEventRepository outboxRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;
    @Autowired private UserDirectoryRepository userDirectoryRepository;

    @MockBean private CoreEventPublisher eventPublisher;
    @MockBean private WorkspacePermissionCache workspacePermissionCache;
    @MockBean private ProjectPermissionCache projectPermissionCache;

    @BeforeEach
    void resetState() {
        reset(eventPublisher, workspacePermissionCache, projectPermissionCache);
        outboxRepository.deleteAll();
        activityRepository.deleteAll();
        labelRelationRepository.deleteAll();
        assigneeRepository.deleteAll();
        issueRepository.deleteAll();
        labelRepository.deleteAll();
        counterRepository.deleteAll();
        stateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userProfileRepository.deleteAll();
        consumedEventRepository.deleteAll();
        userDirectoryRepository.deleteAll();
    }

    @Test
    void concurrentCreatesAllocateUniqueMonotonicNumbersWithinProject() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "Concurrent Issues");
        UUID projectId = project(ownerId, workspaceId, "Project");
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        int attempts = 16;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IssueResponse>> futures = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                int issueIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return issueService.create(ownerId, projectId, request("Issue " + issueIndex, null));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Long> numbers = new HashSet<>();
            for (Future<IssueResponse> future : futures) {
                numbers.add(future.get(20, TimeUnit.SECONDS).issueNumber());
            }
            assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, attempts).boxed().toList());
            assertThat(issueRepository.count()).isEqualTo(attempts);
            assertThat(outboxRepository.findAll()).filteredOn(e -> e.getEventType().equals("issue.created"))
                    .hasSize(attempts);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allIssueAccessIsProjectScopedAndViewerCannotWrite() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID outsiderId = activeUser("outsider@example.com", "Outsider");
        UUID viewerId = activeUser("viewer@example.com", "Viewer");
        UUID workspaceId = workspace(ownerId, "Permissions");
        workspaceMemberService.add(ownerId, workspaceId, viewerId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, viewerId, ProjectRole.VIEWER);
        IssueResponse issue = issueService.create(ownerId, projectId, request("Secret", null));

        assertError(() -> issueService.get(outsiderId, issue.id()), CoreErrorCode.PROJECT_PERMISSION_DENIED);
        assertError(() -> issueService.create(viewerId, projectId, request("No", null)),
                CoreErrorCode.PROJECT_MEMBER_PERMISSION_DENIED);
        assertThat(issueService.get(viewerId, issue.id()).id()).isEqualTo(issue.id());
    }

    @Test
    void assigneeMustRemainActiveButImplicitWorkspaceAdminIsEligible() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID adminId = activeUser("admin@example.com", "Admin");
        UUID workspaceId = workspace(ownerId, "Assignees");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        workspaceMemberService.add(ownerId, workspaceId, adminId, WorkspaceRole.ADMIN);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);

        IssueResponse assigned = issueService.create(
                ownerId,
                projectId,
                new CreateIssueRequest("Assigned", null, null, Set.of(memberId, adminId), Set.of(), null));
        assertThat(assigned.assigneeIds()).containsExactlyInAnyOrder(memberId, adminId);
        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).isEmpty();

        projectMemberService.remove(ownerId, projectId, memberId);
        assertThat(projectMemberRepository.existsByProjectIdAndUserIdAndStatus(
                projectId, memberId, MemberStatus.ACTIVE)).isFalse();
        assertError(
                () -> issueService.replaceAssignees(ownerId, assigned.id(), Set.of(memberId)),
                CoreErrorCode.ISSUE_ASSIGNEE_NOT_ELIGIBLE);
    }

    @Test
    void stateAndLabelIdsCannotCrossProjectsAndFiltersUseRelations() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "Filters");
        UUID projectId = project(ownerId, workspaceId, "First");
        UUID otherProjectId = project(ownerId, workspaceId, "Second");
        UUID labelId = catalogService.createLabel(
                ownerId, projectId, new CreateIssueLabelRequest("Bug", "#ff0000")).id();
        UUID otherLabelId = catalogService.createLabel(
                ownerId, otherProjectId, new CreateIssueLabelRequest("Other", "#00ff00")).id();
        UUID otherStateId = stateRepository.findByProjectIdAndStatusOrderByPositionAsc(
                otherProjectId, com.liteworkflow.core.domain.IssueStateStatus.ACTIVE).getFirst().getId();

        IssueResponse issue = issueService.create(
                ownerId,
                projectId,
                new CreateIssueRequest("Searchable bug", null, null, Set.of(ownerId), Set.of(labelId), null));
        assertThat(issueService.list(ownerId, projectId, "searchable", null, ownerId, labelId, null, 1, 20)
                .records()).extracting(IssueResponse::id).containsExactly(issue.id());
        assertError(
                () -> issueService.changeState(ownerId, issue.id(), new ChangeIssueStateRequest(otherStateId)),
                CoreErrorCode.ISSUE_STATE_INVALID);
        assertError(
                () -> issueService.replaceLabels(ownerId, issue.id(), Set.of(otherLabelId)),
                CoreErrorCode.ISSUE_LABEL_NOT_FOUND);
    }

    @Test
    void duplicateCreateAndRelationRequestsAreIdempotent() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "Idempotency");
        UUID projectId = project(ownerId, workspaceId, "Project");
        UUID requestId = UUID.randomUUID();
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        CreateIssueRequest request = request("Only once", requestId);
        IssueResponse first = issueService.create(ownerId, projectId, request);
        IssueResponse replay = issueService.create(ownerId, projectId, request);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(issueRepository.count()).isOne();
        assertThat(outboxRepository.findAll()).filteredOn(e -> e.getEventType().equals("issue.created"))
                .hasSize(1);

        long before = outboxRepository.count();
        issueService.replaceAssignees(ownerId, first.id(), Set.of(ownerId));
        issueService.replaceAssignees(ownerId, first.id(), Set.of(ownerId));
        assertThat(outboxRepository.count()).isEqualTo(before + 1);

        CreateIssueRequest conflict = new CreateIssueRequest(
                "Different", null, null, Set.of(), Set.of(), requestId);
        assertError(() -> issueService.create(ownerId, projectId, conflict),
                CoreErrorCode.ISSUE_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void issueStateLabelAndIssueCrudWriteMatchingActivityAndOutboxRecords() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "CRUD");
        UUID projectId = project(ownerId, workspaceId, "Project");
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        var label = catalogService.createLabel(
                ownerId, projectId, new CreateIssueLabelRequest("Bug", "#ff0000"));
        label = catalogService.updateLabel(
                ownerId, projectId, label.id(), new UpdateIssueLabelRequest("Defect", "#AA0000"));
        var customState = catalogService.createState(
                ownerId,
                projectId,
                new CreateIssueStateRequest(
                        "Blocked", com.liteworkflow.core.domain.IssueStateCategory.IN_PROGRESS, 3, false));
        customState = catalogService.updateState(
                ownerId,
                projectId,
                customState.id(),
                new UpdateIssueStateRequest("Waiting", null, null, null));

        IssueResponse issue = issueService.create(
                ownerId,
                projectId,
                new CreateIssueRequest("Original", "Details", null, Set.of(ownerId), Set.of(label.id()), null));
        issue = issueService.update(ownerId, issue.id(), new UpdateIssueRequest("Updated", "New details"));
        issue = issueService.changeState(ownerId, issue.id(), new ChangeIssueStateRequest(customState.id()));
        issue = issueService.replaceLabels(ownerId, issue.id(), Set.of());
        assertThat(issue.title()).isEqualTo("Updated");
        assertThat(issue.state().name()).isEqualTo("Waiting");
        assertThat(issue.labels()).isEmpty();

        UUID deletedIssueId = issue.id();
        issueService.delete(ownerId, deletedIssueId);
        assertError(() -> issueService.get(ownerId, deletedIssueId), CoreErrorCode.ISSUE_NOT_FOUND);
        catalogService.deleteState(ownerId, projectId, customState.id());
        catalogService.deleteLabel(ownerId, projectId, label.id());

        assertThat(outboxRepository.count()).isEqualTo(activityRepository.count());
        assertThat(outboxRepository.findAll()).extracting(value -> value.getEventType()).contains(
                "issue.label.created",
                "issue.label.updated",
                "issue.state.created",
                "issue.state.updated",
                "issue.created",
                "issue.updated",
                "issue.state.changed",
                "issue.labels.changed",
                "issue.deleted",
                "issue.state.deleted",
                "issue.label.deleted");
        assertThat(labelRepository.findById(label.id())).get()
                .extracting(value -> value.getStatus()).isEqualTo(IssueLabelStatus.DELETED);
    }

    @Test
    void publisherFailureDoesNotRollbackIssueAndRecoveryPublishesPendingEvent() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "Outbox");
        UUID projectId = project(ownerId, workspaceId, "Project");
        outboxRepository.deleteAll();
        activityRepository.deleteAll();
        coreProperties.getOutbox().setImmediatePublish(true);
        try {
            doThrow(new IllegalStateException("broker unavailable")).when(eventPublisher).publish(any());

            IssueResponse issue = issueService.create(ownerId, projectId, request("Durable", null));
            var event = outboxRepository.findAll().stream()
                    .filter(value -> value.getEventType().equals("issue.created"))
                    .findFirst().orElseThrow();

            assertThat(issueRepository.findActiveById(issue.id())).isPresent();
            assertThat(outboxRepository.findById(event.getId())).get()
                    .extracting(value -> value.getStatus()).isEqualTo(OutboxStatus.FAILED);

            reset(eventPublisher);
            Thread.sleep(10);
            outboxDispatchService.recoverPending();
            assertThat(outboxRepository.findById(event.getId())).get()
                    .extracting(value -> value.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            verify(eventPublisher).publish(any());
        } finally {
            coreProperties.getOutbox().setImmediatePublish(false);
        }
    }

    private CreateIssueRequest request(String title, UUID requestId) {
        return new CreateIssueRequest(title, null, null, Set.of(), Set.of(), requestId);
    }

    private UUID activeUser(String email, String displayName) {
        UUID userId = UUID.randomUUID();
        projectionService.consume(new EventEnvelope<>(
                UUID.randomUUID(),
                "identity.user.registered",
                1,
                Instant.now(),
                new EventScope(null, null, userId),
                userId,
                new IdentityUserEventPayload(userId, email, displayName, AccountStatus.ACTIVE, 1),
                Map.of()));
        return userId;
    }

    private UUID workspace(UUID ownerId, String name) {
        return workspaceService.create(ownerId, new CreateWorkspaceRequest(name, null)).id();
    }

    private UUID project(UUID ownerId, UUID workspaceId, String name) {
        return projectService.create(ownerId, workspaceId, new CreateProjectRequest(name, null)).id();
    }

    private void assertError(Runnable action, CoreErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }
}
