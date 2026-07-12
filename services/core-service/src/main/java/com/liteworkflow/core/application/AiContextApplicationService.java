package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.Activity;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.dto.response.AiIssueContextResponse;
import com.liteworkflow.core.dto.response.AiProjectContextResponse;
import com.liteworkflow.core.dto.response.AiWeeklyReportContextResponse;
import com.liteworkflow.core.dto.response.AiWorkspaceContextResponse;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiContextApplicationService {

    private static final int MAX_DIGEST_CHARS = 30_000;

    private final PermissionService permissions;
    private final ProjectRepository projects;
    private final IssueRepository issues;
    private final ActivityRepository activities;
    private final Clock clock;

    public AiContextApplicationService(
            PermissionService permissions,
            ProjectRepository projects,
            IssueRepository issues,
            ActivityRepository activities,
            Clock clock) {
        this.permissions = permissions;
        this.projects = projects;
        this.issues = issues;
        this.activities = activities;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiWorkspaceContextResponse workspace(UUID workspaceId, UUID userId) {
        permissions.requireWorkspaceMember(workspaceId, userId);
        return new AiWorkspaceContextResponse(workspaceId);
    }

    @Transactional(readOnly = true)
    public AiProjectContextResponse project(UUID projectId, UUID userId) {
        Project project = activeProject(projectId);
        permissions.requireProjectMember(projectId, userId);
        return new AiProjectContextResponse(
                project.getWorkspaceId(), project.getId(), project.getName(), project.getDescription());
    }

    @Transactional(readOnly = true)
    public AiIssueContextResponse issue(
            UUID issueId, UUID expectedProjectId, UUID userId) {
        Issue issue = issues.findActiveById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
        if (expectedProjectId != null && !expectedProjectId.equals(issue.getProjectId())) {
            throw new BizException(CoreErrorCode.ISSUE_NOT_FOUND);
        }
        Project project = activeProject(issue.getProjectId());
        permissions.requireProjectMember(project.getId(), userId);
        Instant from = issue.getCreatedAt().minusSeconds(1);
        Instant to = clock.instant().plusSeconds(1);
        List<Activity> related = activities
                .findTop200ByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        project.getId(), from, to)
                .stream()
                .filter(activity -> issueId.equals(activity.getAggregateId())
                        || activity.getPayloadJson().toString().contains(issueId.toString()))
                .toList();
        return new AiIssueContextResponse(
                project.getWorkspaceId(), project.getId(), issue.getId(), issue.getTitle(),
                issue.getDescription(), digest(related));
    }

    @Transactional(readOnly = true)
    public AiWeeklyReportContextResponse weekly(
            UUID projectId, UUID userId, LocalDate from, LocalDate to) {
        Project project = activeProject(projectId);
        permissions.requireProjectMember(projectId, userId);
        Instant fromInclusive = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Activity> selected = activities
                .findTop200ByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        projectId, fromInclusive, toExclusive);
        return new AiWeeklyReportContextResponse(
                project.getWorkspaceId(), projectId, project.getName(), project.getDescription(), digest(selected));
    }

    private Project activeProject(UUID projectId) {
        return projects.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private static String digest(List<Activity> activities) {
        StringBuilder digest = new StringBuilder();
        for (Activity activity : activities) {
            String line = "%s | %s | %s | %s | %s%n".formatted(
                    activity.getCreatedAt(), activity.getActivityType(), activity.getAggregateType(),
                    activity.getAggregateId(), activity.getPayloadJson());
            if (digest.length() + line.length() > MAX_DIGEST_CHARS) {
                digest.append("[activity digest truncated]");
                break;
            }
            digest.append(line);
        }
        return digest.toString();
    }
}
