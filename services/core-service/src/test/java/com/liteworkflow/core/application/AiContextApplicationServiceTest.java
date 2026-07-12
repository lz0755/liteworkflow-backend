package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiContextApplicationServiceTest {

    private final PermissionService permissions = mock(PermissionService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private AiContextApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AiContextApplicationService(permissions, projects, issues, activities, Clock.systemUTC());
    }

    @Test
    void missingIssueReturnsNotFoundBeforePermissionLookup() {
        UUID issueId = UUID.randomUUID();
        when(issues.findActiveById(issueId)).thenReturn(Optional.empty());

        assertCode(() -> service.issue(issueId, UUID.randomUUID(), UUID.randomUUID()),
                CoreErrorCode.ISSUE_NOT_FOUND);
        verifyNoInteractions(permissions);
    }

    @Test
    void crossProjectIssueIsHiddenAsNotFound() {
        UUID actualProjectId = UUID.randomUUID();
        Issue issue = issue(actualProjectId, "Authoritative title");
        when(issues.findActiveById(issue.getId())).thenReturn(Optional.of(issue));

        assertCode(() -> service.issue(issue.getId(), UUID.randomUUID(), UUID.randomUUID()),
                CoreErrorCode.ISSUE_NOT_FOUND);
        verifyNoInteractions(permissions);
    }

    @Test
    void unauthorizedIssueContextReturnsProjectForbidden() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Issue issue = issue(projectId, "Authoritative title");
        Project project = project(projectId);
        when(issues.findActiveById(issue.getId())).thenReturn(Optional.of(issue));
        when(projects.findActiveById(projectId)).thenReturn(Optional.of(project));
        org.mockito.Mockito.doThrow(new BizException(CoreErrorCode.PROJECT_PERMISSION_DENIED))
                .when(permissions).requireProjectMember(projectId, userId);

        assertCode(() -> service.issue(issue.getId(), projectId, userId),
                CoreErrorCode.PROJECT_PERMISSION_DENIED);
    }

    @Test
    void issueContextComesFromCoreRecordsAfterReadAuthorization() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Issue issue = issue(projectId, "Authoritative title");
        Project project = project(projectId);
        when(issues.findActiveById(issue.getId())).thenReturn(Optional.of(issue));
        when(projects.findActiveById(projectId)).thenReturn(Optional.of(project));
        when(activities.findTop200ByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                org.mockito.ArgumentMatchers.eq(projectId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var context = service.issue(issue.getId(), projectId, userId);

        assertThat(context.title()).isEqualTo("Authoritative title");
        assertThat(context.description()).isEqualTo("Authoritative description");
        assertThat(context.workspaceId()).isEqualTo(project.getWorkspaceId());
        verify(permissions).requireProjectMember(projectId, userId);
    }

    private static Issue issue(UUID projectId, String title) {
        UUID actor = UUID.randomUUID();
        return new Issue(
                UUID.randomUUID(), projectId, 1, title, "Authoritative description",
                UUID.randomUUID(), null, actor, Instant.now());
    }

    private static Project project(UUID projectId) {
        return new Project(
                projectId, UUID.randomUUID(), "Authoritative project", "Core description",
                UUID.randomUUID(), Instant.now());
    }

    private static void assertCode(Runnable action, CoreErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
