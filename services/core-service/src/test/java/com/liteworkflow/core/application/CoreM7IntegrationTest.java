package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.ChangeIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateCommentRequest;
import com.liteworkflow.core.dto.request.CreateIssueRequest;
import com.liteworkflow.core.dto.request.CreateIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.dto.request.UpdateCommentRequest;
import com.liteworkflow.core.dto.response.CommentResponse;
import com.liteworkflow.core.dto.response.IssueResponse;
import com.liteworkflow.core.outbox.CoreEventPublisher;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.IssueAssigneeRepository;
import com.liteworkflow.core.repository.IssueCommentRepository;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueMentionRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.IssueSubscriberRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import com.liteworkflow.core.repository.ProjectIssueCounterRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class CoreM7IntegrationTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private ProjectMemberApplicationService projectMemberService;
    @Autowired private IssueApplicationService issueService;
    @Autowired private IssueCatalogApplicationService catalogService;
    @Autowired private CommentApplicationService commentService;
    @Autowired private IssueSubscriptionApplicationService subscriptionService;
    @Autowired private IssueCommentRepository commentRepository;
    @Autowired private IssueMentionRepository mentionRepository;
    @Autowired private IssueSubscriberRepository subscriberRepository;
    @Autowired private IssueAssigneeRepository assigneeRepository;
    @Autowired private IssueLabelRelationRepository labelRelationRepository;
    @Autowired private IssueRepository issueRepository;
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
    @AfterEach
    void resetState() {
        reset(eventPublisher, workspacePermissionCache, projectPermissionCache);
        outboxRepository.deleteAll();
        activityRepository.deleteAll();
        mentionRepository.deleteAll();
        subscriberRepository.deleteAll();
        commentRepository.deleteAll();
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
    void commentPermissionAndMentionEligibilityAreProjectScoped() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID projectMemberId = activeUser("member@example.com", "Member");
        UUID otherProjectMemberId = activeUser("other@example.com", "Other");
        UUID outsiderId = activeUser("outsider@example.com", "Outsider");
        UUID workspaceId = workspace(ownerId, "Collaboration");
        addWorkspaceMember(ownerId, workspaceId, projectMemberId);
        addWorkspaceMember(ownerId, workspaceId, otherProjectMemberId);
        UUID projectId = project(ownerId, workspaceId, "First");
        UUID otherProjectId = project(ownerId, workspaceId, "Second");
        projectMemberService.add(ownerId, projectId, projectMemberId, ProjectRole.MEMBER);
        projectMemberService.add(ownerId, otherProjectId, otherProjectMemberId, ProjectRole.MEMBER);
        IssueResponse issue = issue(ownerId, projectId, "Mention checks");

        assertError(
                () -> commentService.create(outsiderId, issue.id(), new CreateCommentRequest("not allowed")),
                CoreErrorCode.PROJECT_PERMISSION_DENIED);

        UUID missingId = UUID.randomUUID();
        assertError(
                () -> commentService.create(ownerId, issue.id(), new CreateCommentRequest(token(missingId))),
                CoreErrorCode.MENTION_USER_NOT_FOUND);
        assertError(
                () -> commentService.create(
                        ownerId, issue.id(), new CreateCommentRequest(token(otherProjectMemberId))),
                CoreErrorCode.MENTION_USER_NOT_ELIGIBLE);

        CommentResponse comment = commentService.create(
                ownerId,
                issue.id(),
                new CreateCommentRequest(token(projectMemberId) + " and again " + token(projectMemberId)));
        assertThat(comment.mentionedUserIds()).containsExactly(projectMemberId);
        assertThat(mentionRepository.findByCommentId(comment.id())).hasSize(1);
    }

    @Test
    void viewerCanReadButCannotCommentAndProjectAdminCanModerateAnotherAuthorsComment() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID authorId = activeUser("author@example.com", "Author");
        UUID viewerId = activeUser("viewer@example.com", "Viewer");
        UUID workspaceId = workspace(ownerId, "Comment roles");
        addWorkspaceMember(ownerId, workspaceId, authorId);
        addWorkspaceMember(ownerId, workspaceId, viewerId);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, authorId, ProjectRole.MEMBER);
        projectMemberService.add(ownerId, projectId, viewerId, ProjectRole.VIEWER);
        IssueResponse issue = issue(ownerId, projectId, "Comment permissions");
        CommentResponse comment = commentService.create(
                authorId, issue.id(), new CreateCommentRequest("original"));

        assertThat(commentService.list(viewerId, issue.id(), 1, 20).records())
                .extracting(CommentResponse::id)
                .containsExactly(comment.id());
        assertError(
                () -> commentService.create(viewerId, issue.id(), new CreateCommentRequest("forbidden")),
                CoreErrorCode.PROJECT_MEMBER_PERMISSION_DENIED);

        CommentResponse moderated = commentService.update(
                ownerId, comment.id(), new UpdateCommentRequest("moderated"));
        assertThat(moderated.body()).isEqualTo("moderated");
        commentService.delete(ownerId, comment.id());
        assertThat(commentService.list(ownerId, issue.id(), 1, 20).records()).isEmpty();
    }

    @Test
    void removedProjectMemberCannotBeMentionedOrReceiveSubscriberStateNotification() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID removedMemberId = activeUser("removed@example.com", "Removed");
        UUID workspaceId = workspace(ownerId, "Removed collaborator");
        addWorkspaceMember(ownerId, workspaceId, removedMemberId);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, removedMemberId, ProjectRole.VIEWER);
        IssueResponse issue = issue(ownerId, projectId, "Membership changes");
        var state = catalogService.createState(
                ownerId,
                projectId,
                new CreateIssueStateRequest(
                        "Review", com.liteworkflow.core.domain.IssueStateCategory.IN_PROGRESS, 10, false));
        subscriptionService.subscribe(removedMemberId, issue.id());

        projectMemberService.remove(ownerId, projectId, removedMemberId);
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        assertError(
                () -> commentService.create(
                        ownerId, issue.id(), new CreateCommentRequest(token(removedMemberId))),
                CoreErrorCode.MENTION_USER_NOT_ELIGIBLE);
        issueService.changeState(ownerId, issue.id(), new ChangeIssueStateRequest(state.id()));

        String stateEvent = outboxRepository.findAll().stream()
                .filter(value -> value.getEventType().equals("issue.state.changed"))
                .findFirst().orElseThrow().getPayloadJson();
        assertThat(stateEvent).doesNotContain(removedMemberId.toString());
        assertThat(subscriberRepository.findUserIdsByIssueId(issue.id()))
                .containsExactly(removedMemberId);
    }

    @Test
    void editingComputesMentionDiffAndOnlyNotifiesNewMentions() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID firstId = activeUser("first@example.com", "First");
        UUID secondId = activeUser("second@example.com", "Second");
        UUID editorId = activeUser("editor@example.com", "Editor");
        UUID workspaceId = workspace(ownerId, "Mention diff");
        addWorkspaceMember(ownerId, workspaceId, firstId);
        addWorkspaceMember(ownerId, workspaceId, secondId);
        addWorkspaceMember(ownerId, workspaceId, editorId);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, firstId, ProjectRole.MEMBER);
        projectMemberService.add(ownerId, projectId, secondId, ProjectRole.MEMBER);
        projectMemberService.add(ownerId, projectId, editorId, ProjectRole.MEMBER);
        IssueResponse issue = issue(ownerId, projectId, "Editing");
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        String sensitive = "private-token-should-not-enter-events";
        CommentResponse comment = commentService.create(
                ownerId, issue.id(), new CreateCommentRequest(sensitive + " " + token(firstId)));
        CommentResponse updated = commentService.update(
                ownerId,
                comment.id(),
                new UpdateCommentRequest("revised " + markdownToken(secondId) + " " + markdownToken(secondId)));

        assertThat(updated.mentionedUserIds()).containsExactly(secondId);
        assertThat(mentionRepository.findByCommentId(comment.id()))
                .extracting(value -> value.getUserId())
                .containsExactly(secondId);
        List<String> mentionEvents = outboxRepository.findAll().stream()
                .filter(value -> value.getEventType().equals("comment.mentioned"))
                .map(value -> value.getPayloadJson())
                .toList();
        assertThat(mentionEvents).hasSize(2);
        assertThat(mentionEvents.get(0)).contains(firstId.toString()).doesNotContain(secondId.toString());
        assertThat(mentionEvents.get(1)).contains(secondId.toString()).doesNotContain(firstId.toString());
        assertThat(outboxRepository.findAll())
                .allSatisfy(value -> assertThat(value.getPayloadJson()).doesNotContain(sensitive));

        assertError(
                () -> commentService.update(editorId, comment.id(), new UpdateCommentRequest("takeover")),
                CoreErrorCode.COMMENT_MODIFICATION_DENIED);
    }

    @Test
    void softDeleteHidesCommentAndErasesBodyWhileKeepingAuditMetadata() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Deletion");
        addWorkspaceMember(ownerId, workspaceId, memberId);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);
        IssueResponse issue = issue(ownerId, projectId, "Delete comment");
        String sensitive = "customer-secret-body";
        CommentResponse comment = commentService.create(
                ownerId, issue.id(), new CreateCommentRequest(sensitive + " " + token(memberId)));

        commentService.delete(ownerId, comment.id());

        assertThat(commentService.list(ownerId, issue.id(), 1, 20).records()).isEmpty();
        assertThat(commentRepository.findById(comment.id())).get().satisfies(value -> {
            assertThat(value.getBody()).isNull();
            assertThat(value.getDeletedAt()).isNotNull();
            assertThat(value.getAuthorId()).isEqualTo(ownerId);
            assertThat(value.getIssueId()).isEqualTo(issue.id());
        });
        assertThat(mentionRepository.findByCommentId(comment.id())).isEmpty();
        String deletionEvent = outboxRepository.findAll().stream()
                .filter(value -> value.getEventType().equals("comment.deleted"))
                .findFirst().orElseThrow().getPayloadJson();
        assertThat(deletionEvent).contains(comment.id().toString(), issue.id().toString())
                .doesNotContain(sensitive);
        assertError(
                () -> commentService.update(ownerId, comment.id(), new UpdateCommentRequest("resurrect")),
                CoreErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void subscriptionsAreIdempotentAndStateEventsTargetCurrentSubscribers() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID subscriberId = activeUser("subscriber@example.com", "Subscriber");
        UUID workspaceId = workspace(ownerId, "Subscriptions");
        addWorkspaceMember(ownerId, workspaceId, subscriberId);
        UUID projectId = project(ownerId, workspaceId, "Project");
        projectMemberService.add(ownerId, projectId, subscriberId, ProjectRole.VIEWER);
        IssueResponse issue = issue(ownerId, projectId, "Watched issue");
        var state = catalogService.createState(
                ownerId,
                projectId,
                new CreateIssueStateRequest(
                        "Review", com.liteworkflow.core.domain.IssueStateCategory.IN_PROGRESS, 10, false));
        outboxRepository.deleteAll();
        activityRepository.deleteAll();

        subscriptionService.subscribe(subscriberId, issue.id());
        subscriptionService.subscribe(subscriberId, issue.id());
        assertThat(subscriberRepository.findUserIdsByIssueId(issue.id())).containsExactly(subscriberId);
        assertThat(outboxRepository.findAll())
                .filteredOn(value -> value.getEventType().equals("issue.subscriber.added"))
                .hasSize(1);

        issueService.changeState(ownerId, issue.id(), new ChangeIssueStateRequest(state.id()));
        String stateEvent = outboxRepository.findAll().stream()
                .filter(value -> value.getEventType().equals("issue.state.changed"))
                .findFirst().orElseThrow().getPayloadJson();
        assertThat(stateEvent).contains(subscriberId.toString());

        subscriptionService.unsubscribe(subscriberId, issue.id());
        assertThat(subscriberRepository.findUserIdsByIssueId(issue.id())).isEmpty();
        assertThat(outboxRepository.findAll()).extracting(value -> value.getEventType())
                .contains("issue.subscriber.added", "issue.state.changed", "issue.subscriber.removed");
        assertThat(activityRepository.count()).isEqualTo(outboxRepository.count());
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

    private void addWorkspaceMember(UUID ownerId, UUID workspaceId, UUID userId) {
        workspaceMemberService.add(ownerId, workspaceId, userId, WorkspaceRole.MEMBER);
    }

    private IssueResponse issue(UUID ownerId, UUID projectId, String title) {
        return issueService.create(
                ownerId, projectId, new CreateIssueRequest(title, null, null, Set.of(), Set.of(), null));
    }

    private String token(UUID userId) {
        return "<@" + userId + ">";
    }

    private String markdownToken(UUID userId) {
        return "@[display label](user:" + userId + ")";
    }

    private void assertError(Runnable action, CoreErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }
}
