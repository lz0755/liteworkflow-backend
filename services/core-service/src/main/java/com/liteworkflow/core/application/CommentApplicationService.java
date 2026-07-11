package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.IssueComment;
import com.liteworkflow.core.domain.IssueMention;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.dto.request.CreateCommentRequest;
import com.liteworkflow.core.dto.request.UpdateCommentRequest;
import com.liteworkflow.core.dto.response.CommentResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.repository.IssueCommentRepository;
import com.liteworkflow.core.repository.IssueMentionRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentApplicationService {

    private static final String COMMENT_AGGREGATE = "COMMENT";

    private final PermissionService permissionService;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final IssueCommentRepository commentRepository;
    private final IssueMentionRepository mentionRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MentionParser mentionParser;
    private final ActivityOutboxService activityOutboxService;
    private final Clock clock;

    public CommentApplicationService(
            PermissionService permissionService,
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            IssueCommentRepository commentRepository,
            IssueMentionRepository mentionRepository,
            UserDirectoryRepository userDirectoryRepository,
            ProjectMemberRepository projectMemberRepository,
            MentionParser mentionParser,
            ActivityOutboxService activityOutboxService,
            Clock clock) {
        this.permissionService = permissionService;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.mentionParser = mentionParser;
        this.activityOutboxService = activityOutboxService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<CommentResponse> list(UUID actorId, UUID issueId, int page, int size) {
        validatePage(page, size);
        Issue issue = requireIssue(issueId);
        permissionService.requireProjectMember(issue.getProjectId(), actorId);
        var comments = commentRepository.findByIssueIdAndDeletedAtIsNull(
                issueId,
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
        return PageResult.of(assemble(comments.getContent()), comments.getTotalElements(), page, size);
    }

    @Transactional
    public CommentResponse create(UUID actorId, UUID issueId, CreateCommentRequest request) {
        Issue issue = requireIssue(issueId);
        permissionService.requireIssueWriter(issue.getProjectId(), actorId);
        String body = normalizeBody(request.body());
        Set<UUID> mentionedUserIds = mentionParser.parse(body);
        validateMentions(issue.getProjectId(), mentionedUserIds);

        Instant now = clock.instant();
        IssueComment comment = commentRepository.save(
                new IssueComment(UUID.randomUUID(), issueId, actorId, body, now));
        saveMentions(comment.getId(), mentionedUserIds, actorId, now);

        Project project = requireProject(issue.getProjectId());
        activityOutboxService.recordProjectChange(
                "comment.created",
                project.getWorkspaceId(),
                project.getId(),
                COMMENT_AGGREGATE,
                comment.getId(),
                actorId,
                commentMetadata(comment, mentionedUserIds));
        recordMentionEvents(project, comment, actorId, mentionedUserIds, Set.of());
        return toResponse(comment, mentionedUserIds);
    }

    @Transactional
    public CommentResponse update(UUID actorId, UUID commentId, UpdateCommentRequest request) {
        UUID issueId = commentRepository.findActiveIssueId(commentId)
                .orElseThrow(() -> new BizException(CoreErrorCode.COMMENT_NOT_FOUND));
        Issue issue = requireIssue(issueId);
        ProjectRole role = permissionService.requireIssueWriter(issue.getProjectId(), actorId);
        IssueComment comment = commentRepository.findActiveByIdForUpdate(commentId)
                .orElseThrow(() -> new BizException(CoreErrorCode.COMMENT_NOT_FOUND));
        requireCanModify(comment, actorId, role);

        String body = normalizeBody(request.body());
        Set<UUID> requestedMentions = mentionParser.parse(body);
        validateMentions(issue.getProjectId(), requestedMentions);
        Set<UUID> existingMentions = mentionRepository.findByCommentId(commentId).stream()
                .map(IssueMention::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> addedMentions = difference(requestedMentions, existingMentions);
        Set<UUID> removedMentions = difference(existingMentions, requestedMentions);
        if (body.equals(comment.getBody()) && addedMentions.isEmpty() && removedMentions.isEmpty()) {
            return toResponse(comment, existingMentions);
        }

        Instant now = clock.instant();
        comment.update(body, actorId, now);
        if (!removedMentions.isEmpty()) {
            mentionRepository.deleteByCommentIdAndUserIdIn(commentId, removedMentions);
        }
        saveMentions(commentId, addedMentions, actorId, now);

        Project project = requireProject(issue.getProjectId());
        activityOutboxService.recordProjectChange(
                "comment.updated",
                project.getWorkspaceId(),
                project.getId(),
                COMMENT_AGGREGATE,
                commentId,
                actorId,
                commentMetadata(comment, requestedMentions));
        recordMentionEvents(project, comment, actorId, addedMentions, removedMentions);
        return toResponse(comment, requestedMentions);
    }

    @Transactional
    public void delete(UUID actorId, UUID commentId) {
        UUID issueId = commentRepository.findActiveIssueId(commentId)
                .orElseThrow(() -> new BizException(CoreErrorCode.COMMENT_NOT_FOUND));
        Issue issue = requireIssue(issueId);
        ProjectRole role = permissionService.requireIssueWriter(issue.getProjectId(), actorId);
        IssueComment comment = commentRepository.findActiveByIdForUpdate(commentId)
                .orElseThrow(() -> new BizException(CoreErrorCode.COMMENT_NOT_FOUND));
        requireCanModify(comment, actorId, role);

        Instant now = clock.instant();
        mentionRepository.deleteByCommentId(commentId);
        comment.delete(actorId, now);
        Project project = requireProject(issue.getProjectId());
        activityOutboxService.recordProjectChange(
                "comment.deleted",
                project.getWorkspaceId(),
                project.getId(),
                COMMENT_AGGREGATE,
                commentId,
                actorId,
                Map.of(
                        "commentId", commentId,
                        "issueId", issueId,
                        "authorId", comment.getAuthorId(),
                        "deletedBy", actorId,
                        "deletedAt", now));
    }

    private void recordMentionEvents(
            Project project,
            IssueComment comment,
            UUID actorId,
            Set<UUID> addedMentions,
            Set<UUID> removedMentions) {
        if (!addedMentions.isEmpty() || !removedMentions.isEmpty()) {
            activityOutboxService.recordProjectChange(
                    "comment.mentions.changed",
                    project.getWorkspaceId(),
                    project.getId(),
                    COMMENT_AGGREGATE,
                    comment.getId(),
                    actorId,
                    Map.of(
                            "commentId", comment.getId(),
                            "issueId", comment.getIssueId(),
                            "addedMentionUserIds", addedMentions,
                            "removedMentionUserIds", removedMentions,
                            "changedAt", comment.getUpdatedAt()));
        }
        Set<UUID> notificationRecipients = addedMentions.stream()
                .filter(userId -> !userId.equals(actorId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!notificationRecipients.isEmpty()) {
            activityOutboxService.recordProjectChange(
                    "comment.mentioned",
                    project.getWorkspaceId(),
                    project.getId(),
                    COMMENT_AGGREGATE,
                    comment.getId(),
                    actorId,
                    Map.of(
                            "commentId", comment.getId(),
                            "issueId", comment.getIssueId(),
                            "recipientUserIds", notificationRecipients,
                            "mentionedAt", comment.getUpdatedAt()));
        }
    }

    private Map<String, Object> commentMetadata(IssueComment comment, Set<UUID> mentionedUserIds) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("commentId", comment.getId());
        metadata.put("issueId", comment.getIssueId());
        metadata.put("authorId", comment.getAuthorId());
        metadata.put("mentionedUserIds", mentionedUserIds);
        metadata.put("createdAt", comment.getCreatedAt());
        metadata.put("updatedAt", comment.getUpdatedAt());
        return metadata;
    }

    private void validateMentions(UUID projectId, Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, UserDirectory> users = userDirectoryRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserDirectory::getUserId, user -> user));
        if (users.size() != userIds.size()) {
            throw new BizException(CoreErrorCode.MENTION_USER_NOT_FOUND);
        }
        if (users.values().stream().anyMatch(user -> user.getAccountStatus() != AccountStatus.ACTIVE)) {
            throw new BizException(CoreErrorCode.MENTION_USER_NOT_ELIGIBLE);
        }
        Set<UUID> eligible = new HashSet<>(projectMemberRepository.findEligibleAssigneeIds(
                projectId, new ArrayList<>(userIds)));
        if (!eligible.equals(new HashSet<>(userIds))) {
            throw new BizException(CoreErrorCode.MENTION_USER_NOT_ELIGIBLE);
        }
    }

    private List<CommentResponse> assemble(List<IssueComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<UUID>> mentionIds = new HashMap<>();
        mentionRepository.findByCommentIdIn(comments.stream().map(IssueComment::getId).toList())
                .forEach(mention -> mentionIds
                        .computeIfAbsent(mention.getCommentId(), ignored -> new LinkedHashSet<>())
                        .add(mention.getUserId()));
        return comments.stream()
                .map(comment -> toResponse(comment, mentionIds.getOrDefault(comment.getId(), Set.of())))
                .toList();
    }

    private CommentResponse toResponse(IssueComment comment, Collection<UUID> mentionIds) {
        return new CommentResponse(
                comment.getId(),
                comment.getIssueId(),
                comment.getAuthorId(),
                comment.getBody(),
                Set.copyOf(mentionIds),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    private void saveMentions(UUID commentId, Set<UUID> userIds, UUID actorId, Instant now) {
        mentionRepository.saveAll(userIds.stream()
                .map(userId -> new IssueMention(commentId, userId, actorId, now))
                .toList());
    }

    private Set<UUID> difference(Set<UUID> left, Set<UUID> right) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private void requireCanModify(IssueComment comment, UUID actorId, ProjectRole role) {
        if (!comment.getAuthorId().equals(actorId) && role != ProjectRole.PROJECT_ADMIN) {
            throw new BizException(CoreErrorCode.COMMENT_MODIFICATION_DENIED);
        }
    }

    private Issue requireIssue(UUID issueId) {
        return issueRepository.findActiveById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private String normalizeBody(String body) {
        String normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty() || normalized.length() > 10000) {
            throw new BizException(CoreErrorCode.COMMENT_BODY_INVALID);
        }
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(com.liteworkflow.common.core.error.CommonErrorCode.VALIDATION_ERROR);
        }
    }
}
