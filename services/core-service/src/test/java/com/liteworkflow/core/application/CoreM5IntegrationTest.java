package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.error.BizException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CoreM5IntegrationTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private ProjectMemberApplicationService projectMemberService;
    @Autowired private PermissionService permissionService;
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
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
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
    void projectCrudCreatesExplicitAdminAndDefaultIssueStates() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspace(ownerId, "Project CRUD");

        String body = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects", workspaceId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  First Project  \",\"description\":\"Initial\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("First Project"))
                .andExpect(jsonPath("$.data.currentUserRole").value("PROJECT_ADMIN"))
                .andReturn().getResponse().getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());

        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .get().extracting(member -> member.getRole()).isEqualTo(ProjectRole.PROJECT_ADMIN);
        assertThat(issueStateRepository.findByProjectIdOrderByPositionAsc(projectId))
                .extracting(state -> state.getName()).containsExactly("To Do", "In Progress", "Done");
        assertThat(issueStateRepository.findByProjectIdOrderByPositionAsc(projectId))
                .filteredOn(state -> state.isDefaultState()).singleElement()
                .extracting(state -> state.getName()).isEqualTo("To Do");

        mockMvc.perform(patch("/api/v1/projects/{projectId}", projectId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Project\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Project"));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/projects", workspaceId)
                        .headers(identityHeaders(ownerId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId)
                        .headers(identityHeaders(ownerId, "owner@example.com")))
                .andExpect(status().isOk());
        assertThat(projectRepository.findActiveById(projectId)).isEmpty();
    }

    @Test
    void projectSearchOnlyReturnsActiveMembersOfOwningWorkspaceAndExcludesExistingMembers()
            throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID candidateId = activeUser("candidate@example.com", "Candidate User");
        UUID outsideId = activeUser("outside@example.com", "Candidate Outside");
        UUID workspaceId = workspace(ownerId, "Owning Workspace");
        UUID otherWorkspaceId = workspace(outsideId, "Other Workspace");
        workspaceMemberService.add(ownerId, workspaceId, candidateId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Search Project");

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "candidate")
                        .param("contextType", "PROJECT")
                        .param("contextId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(candidateId.toString()))
                .andExpect(jsonPath("$.data.records[0].workspaceMember").value(true))
                .andExpect(jsonPath("$.data.records[0].projectMember").value(false));

        projectMemberService.add(ownerId, projectId, candidateId, ProjectRole.MEMBER);
        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "candidate")
                        .param("contextType", "PROJECT")
                        .param("contextId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "candidate")
                        .param("contextType", "PROJECT")
                        .param("contextId", projectId.toString())
                        .param("excludeExisting", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].projectMember").value(true))
                .andExpect(jsonPath("$.data.records[0].eligible").value(false));

        assertThat(otherWorkspaceId).isNotEqualTo(workspaceId);
    }

    @Test
    void nonWorkspaceMemberCannotBeAddedAndCrossWorkspaceIdsDoNotAuthorizeAccess() {
        UUID firstOwner = activeUser("first@example.com", "First Owner");
        UUID secondOwner = activeUser("second@example.com", "Second Owner");
        UUID firstWorkspace = workspace(firstOwner, "First Workspace");
        UUID secondWorkspace = workspace(secondOwner, "Second Workspace");
        UUID secondProject = project(secondOwner, secondWorkspace, "Second Project");

        assertError(
                () -> projectMemberService.add(secondOwner, secondProject, firstOwner, ProjectRole.MEMBER),
                CoreErrorCode.PROJECT_MEMBER_REQUIRES_WORKSPACE_MEMBER);
        assertError(
                () -> projectService.get(firstOwner, secondProject),
                CoreErrorCode.PROJECT_PERMISSION_DENIED);
        assertThat(projectService.list(firstOwner, firstWorkspace, 1, 20).total()).isZero();
    }

    @Test
    void workspaceAdminManagesProjectImplicitlyWithoutCreatingMembership() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID adminId = activeUser("admin@example.com", "Workspace Admin");
        UUID candidateId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Implicit Admin");
        workspaceMemberService.add(ownerId, workspaceId, adminId, WorkspaceRole.ADMIN);
        workspaceMemberService.add(ownerId, workspaceId, candidateId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Managed Project");

        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).isEmpty();
        projectMemberService.add(adminId, projectId, candidateId, ProjectRole.MEMBER);
        projectMemberService.changeRole(adminId, projectId, candidateId, ProjectRole.VIEWER);
        projectMemberService.remove(adminId, projectId, candidateId);

        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).isEmpty();
        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, candidateId))
                .get().extracting(member -> member.getStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    void duplicateMembersAreRejectedAndProjectMemberEventsAreVersionedWithActivity() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Events");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Events Project");

        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);
        assertError(
                () -> projectMemberService.add(ownerId, projectId, memberId, ProjectRole.VIEWER),
                CoreErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);

        var addedEvents = outboxRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("project.member.added"))
                .toList();
        assertThat(addedEvents).hasSize(2);
        assertThat(addedEvents).allSatisfy(event -> assertThat(event.getPayloadJson())
                .contains("\"version\":1", "\"projectId\":\"" + projectId + "\""));
        assertThat(activityRepository.findAll()).filteredOn(activity ->
                activity.getActivityType().equals("project.member.added")).hasSize(2);
    }

    @Test
    void lastExplicitProjectAdminCannotBeDemotedOrRemovedEvenByImplicitAdmin() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID adminId = activeUser("admin@example.com", "Admin");
        UUID workspaceId = workspace(ownerId, "Last Admin");
        workspaceMemberService.add(ownerId, workspaceId, adminId, WorkspaceRole.ADMIN);
        UUID projectId = project(ownerId, workspaceId, "Last Admin Project");

        assertError(
                () -> projectMemberService.changeRole(
                        ownerId, projectId, ownerId, ProjectRole.MEMBER),
                CoreErrorCode.PROJECT_LAST_ADMIN_REQUIRED);
        assertError(
                () -> projectMemberService.remove(adminId, projectId, ownerId),
                CoreErrorCode.PROJECT_LAST_ADMIN_REQUIRED);
        assertThat(projectMemberRepository.countByProjectIdAndStatusAndRole(
                projectId, MemberStatus.ACTIVE, ProjectRole.PROJECT_ADMIN)).isEqualTo(1);
    }

    @Test
    void concurrentAdminDemotionsCannotRemoveEveryExplicitAdmin() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID secondAdminId = activeUser("second@example.com", "Second Admin");
        UUID workspaceId = workspace(ownerId, "Concurrent Project Admins");
        workspaceMemberService.add(ownerId, workspaceId, secondAdminId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Concurrent Project");
        projectMemberService.add(ownerId, projectId, secondAdminId, ProjectRole.PROJECT_ADMIN);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> attempts = List.of(
                    executor.submit(() -> concurrentDemotion(ready, start, ownerId, projectId)),
                    executor.submit(() -> concurrentDemotion(ready, start, secondAdminId, projectId)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> attempt : attempts) {
                outcomes.add(attempt.get(15, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull).singleElement()
                    .isInstanceOf(BizException.class);
            assertThat(projectMemberRepository.countByProjectIdAndStatusAndRole(
                    projectId, MemberStatus.ACTIVE, ProjectRole.PROJECT_ADMIN)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void removingWorkspaceMemberAtomicallyRemovesProjectMembershipAndInvalidatesBothCaches() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Immediate Revocation");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Revocation Project");
        UUID secondProjectId = project(ownerId, workspaceId, "Second Revocation Project");
        projectMemberService.add(ownerId, projectId, memberId, ProjectRole.MEMBER);
        projectMemberService.add(ownerId, secondProjectId, memberId, ProjectRole.VIEWER);
        assertThat(permissionService.canReadProject(projectId, memberId)).isTrue();
        assertThat(permissionService.canReadProject(secondProjectId, memberId)).isTrue();
        reset(workspacePermissionCache, projectPermissionCache);

        workspaceMemberService.remove(ownerId, workspaceId, memberId);

        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndStatus(
                workspaceId, memberId, MemberStatus.ACTIVE)).isFalse();
        assertThat(projectMemberRepository.existsByProjectIdAndUserIdAndStatus(
                projectId, memberId, MemberStatus.ACTIVE)).isFalse();
        assertThat(projectMemberRepository.existsByProjectIdAndUserIdAndStatus(
                secondProjectId, memberId, MemberStatus.ACTIVE)).isFalse();
        assertThat(permissionService.canReadProject(projectId, memberId)).isFalse();
        assertThat(permissionService.canReadProject(secondProjectId, memberId)).isFalse();
        verify(workspacePermissionCache).evict(workspaceId, memberId);
        verify(projectPermissionCache).evict(projectId, memberId);
        verify(projectPermissionCache).evict(secondProjectId, memberId);
        assertThat(outboxRepository.findAll()).filteredOn(event ->
                event.getEventType().equals("project.member.removed")).hasSize(2);
        assertThat(activityRepository.findAll()).filteredOn(activity ->
                activity.getActivityType().equals("project.member.removed")).hasSize(2);
    }

    @Test
    void projectMemberHttpSupportsPostPatchGetAndDelete() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspace(ownerId, "Member HTTP");
        workspaceMemberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);
        UUID projectId = project(ownerId, workspaceId, "Member API");

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", memberId, "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        mockMvc.perform(patch("/api/v1/projects/{projectId}/members/{userId}", projectId, memberId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .headers(identityHeaders(ownerId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", projectId, memberId)
                        .headers(identityHeaders(ownerId, "owner@example.com")))
                .andExpect(status().isOk());
    }

    private Throwable concurrentDemotion(
            CountDownLatch ready, CountDownLatch start, UUID actorId, UUID projectId) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            projectMemberService.changeRole(actorId, projectId, actorId, ProjectRole.MEMBER);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
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

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, CoreErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(expected);
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
