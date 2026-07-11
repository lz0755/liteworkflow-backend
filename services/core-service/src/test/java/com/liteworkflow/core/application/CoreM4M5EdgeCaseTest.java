package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.outbox.CoreEventPublisher;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Edge-case coverage that complements {@link CoreM4IntegrationTest} and {@link CoreM5IntegrationTest}.
 * These scenarios target code paths that are implemented but not exercised by the milestone suites:
 * reactivating a removed member (soft-delete + unique constraint), project-deletion cache
 * invalidation, and the VIEWER role being denied candidate search.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreM4M5EdgeCaseTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private ProjectMemberApplicationService projectMemberService;
    @Autowired private UserDirectoryRepository userDirectoryRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private IssueStateRepository issueStateRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private LocalOutboxEventRepository outboxRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;

    @MockBean private CoreEventPublisher coreEventPublisher;
    @MockBean private WorkspacePermissionCache workspacePermissionCache;
    @MockBean private ProjectPermissionCache projectPermissionCache;

    @BeforeEach
    void resetState() {
        reset(coreEventPublisher, workspacePermissionCache, projectPermissionCache);
        outboxRepository.deleteAll();
        activityRepository.deleteAll();
        issueStateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userProfileRepository.deleteAll();
        consumedEventRepository.deleteAll();
        userDirectoryRepository.deleteAll();
    }

    @Test
    void removedWorkspaceMemberIsReactivatedWithNewRoleInsteadOfDuplicating() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Readd Workspace");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID originalRowId = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, memberId).orElseThrow().getId();

        workspaceMemberService.remove(ownerId, workspaceId, memberId);
        var removed = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, memberId).orElseThrow();
        assertThat(removed.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(removed.getId()).isEqualTo(originalRowId);

        // Re-adding after removal must reactivate the existing row with the new role, never insert a
        // duplicate (the UNIQUE(workspace_id, user_id) constraint would otherwise be violated).
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.VIEWER);

        var reactivated = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, memberId).orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(originalRowId);
        assertThat(reactivated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reactivated.getRole()).isEqualTo(WorkspaceRole.VIEWER);
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndStatus(
                workspaceId, memberId, MemberStatus.ACTIVE)).isTrue();
    }

    @Test
    void removedProjectMemberIsReactivatedWithNewRoleInsteadOfDuplicating() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Readd Project Workspace");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Readd Project");
        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);
        UUID originalRowId = projectMemberRepository
                .findByProjectIdAndUserId(projectId, memberId).orElseThrow().getId();

        projectMemberService.remove(ownerId, projectId, memberId);
        var removed = projectMemberRepository
                .findByProjectIdAndUserId(projectId, memberId).orElseThrow();
        assertThat(removed.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(removed.getId()).isEqualTo(originalRowId);

        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.VIEWER);

        var reactivated = projectMemberRepository
                .findByProjectIdAndUserId(projectId, memberId).orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(originalRowId);
        assertThat(reactivated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reactivated.getRole()).isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void deletingProjectInvalidatesPermissionCacheForEveryAffectedUser() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Delete Cache Workspace");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Delete Cache Project");
        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);
        reset(projectPermissionCache);

        projectService.delete(ownerId, projectId);

        // Both the explicit project admin (owner) and the project member must have their cached
        // project permission evicted so no stale access survives the deletion.
        verify(projectPermissionCache).evict(projectId, ownerId);
        verify(projectPermissionCache).evict(projectId, memberId);
        assertThat(projectRepository.findActiveById(projectId)).isEmpty();
    }

    @Test
    void viewerCannotSearchCandidates() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID viewerId = activeUser("viewer@example.com", "Viewer");
        activeUser("candidate@example.com", "Candidate Person");
        UUID workspaceId = workspace(ownerId, "Viewer Search Workspace");
        workspaceMemberService.add(ownerId, workspaceId, viewerId, WorkspaceRole.VIEWER);

        // VIEWER is a non-managing role: the plan restricts candidate search to OWNER/ADMIN, so a
        // VIEWER must be denied just like an ordinary MEMBER.
        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(viewerId, "viewer@example.com"))
                        .param("keyword", "candidate")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED.code()));
    }

    private UUID workspace(UUID ownerId, String name) {
        return workspaceService.create(ownerId, new CreateWorkspaceRequest(name, null)).id();
    }

    private UUID project(UUID ownerId, UUID workspaceId, String name) {
        return projectService.create(ownerId, workspaceId, new CreateProjectRequest(name, null)).id();
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

    private org.springframework.http.HttpHeaders identityHeaders(UUID userId, String username) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwtTokenService.issueAccessToken(
                new CurrentUser(userId, username, java.util.Set.of("USER"))));
        headers.set("X-User-Id", userId.toString());
        headers.set("X-Username", username);
        headers.set("X-User-Roles", "USER");
        return headers;
    }
}
