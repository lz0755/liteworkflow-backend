package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.IssueAssignee;
import com.liteworkflow.core.domain.IssueComment;
import com.liteworkflow.core.domain.IssueLabel;
import com.liteworkflow.core.domain.IssueLabelRelation;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.dto.response.RagSourceResponse;
import com.liteworkflow.core.repository.IssueAssigneeRepository;
import com.liteworkflow.core.repository.IssueCommentRepository;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagSourceApplicationService {

    private final IssueRepository issues;
    private final IssueCommentRepository comments;
    private final ProjectRepository projects;
    private final IssueStateRepository states;
    private final IssueAssigneeRepository assignees;
    private final IssueLabelRelationRepository labelRelations;
    private final IssueLabelRepository labels;
    private final UserDirectoryRepository users;

    public RagSourceApplicationService(
            IssueRepository issues,
            IssueCommentRepository comments,
            ProjectRepository projects,
            IssueStateRepository states,
            IssueAssigneeRepository assignees,
            IssueLabelRelationRepository labelRelations,
            IssueLabelRepository labels,
            UserDirectoryRepository users) {
        this.issues = issues;
        this.comments = comments;
        this.projects = projects;
        this.states = states;
        this.assignees = assignees;
        this.labelRelations = labelRelations;
        this.labels = labels;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public RagSourceResponse issue(UUID issueId) {
        Issue issue = issues.findById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
        Project project = project(issue.getProjectId());
        String title = "Issue #" + issue.getIssueNumber() + ": " + issue.getTitle();
        if (issue.isDeleted()) {
            return new RagSourceResponse(project.getWorkspaceId(), project.getId(), issueId,
                    issue.getRowVersion(), true, title, null);
        }
        String state = states.findById(issue.getStateId()).map(value -> value.getName()).orElse("Unknown");
        List<UUID> assigneeIds = assignees.findByIdIssueId(issueId).stream()
                .map(IssueAssignee::getUserId).toList();
        String assigneeNames = users.findAllById(assigneeIds).stream()
                .map(value -> value.getDisplayName()).sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        List<UUID> labelIds = labelRelations.findByIdIssueId(issueId).stream()
                .map(IssueLabelRelation::getLabelId).toList();
        String labelNames = labels.findAllById(labelIds).stream()
                .map(IssueLabel::getName).sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        String text = lines(
                "Issue #" + issue.getIssueNumber(),
                "Title: " + issue.getTitle(),
                issue.getDescription() == null ? null : "Description: " + issue.getDescription(),
                "State: " + state,
                "Labels: " + labelNames,
                "Assignees: " + assigneeNames);
        return new RagSourceResponse(project.getWorkspaceId(), project.getId(), issueId,
                issue.getRowVersion(), false, title, text);
    }

    @Transactional(readOnly = true)
    public RagSourceResponse comment(UUID commentId) {
        IssueComment comment = comments.findById(commentId)
                .orElseThrow(() -> new BizException(CoreErrorCode.COMMENT_NOT_FOUND));
        Issue issue = issues.findById(comment.getIssueId())
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
        Project project = project(issue.getProjectId());
        String title = "Comment on Issue #" + issue.getIssueNumber() + ": " + issue.getTitle();
        if (comment.getDeletedAt() != null || issue.isDeleted()) {
            return new RagSourceResponse(project.getWorkspaceId(), project.getId(), commentId,
                    comment.getRowVersion(), true, title, null);
        }
        String author = users.findById(comment.getAuthorId())
                .map(value -> value.getDisplayName()).orElse("Unknown");
        String text = lines(title, "Author: " + author, "Comment: " + comment.getBody());
        return new RagSourceResponse(project.getWorkspaceId(), project.getId(), commentId,
                comment.getRowVersion(), false, title, text);
    }

    @Transactional(readOnly = true)
    public List<RagSourceResponse> deletedIssueComments(UUID issueId) {
        Issue issue = issues.findById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
        if (!issue.isDeleted()) throw new BizException(CoreErrorCode.ISSUE_NOT_FOUND);
        Project project = project(issue.getProjectId());
        String title = "Comment on Issue #" + issue.getIssueNumber() + ": " + issue.getTitle();
        return comments.findByIssueId(issueId).stream()
                .map(comment -> new RagSourceResponse(
                        project.getWorkspaceId(), project.getId(), comment.getId(),
                        Math.addExact(comment.getRowVersion(), 1), true, title, null))
                .toList();
    }

    private Project project(UUID projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private static String lines(String... values) {
        return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
