package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "debug=false")
@Transactional
class MemberPermissionMatrixTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService workspaceMemberService;
    @Autowired private ProjectApplicationService projectService;
    @Autowired private ProjectMemberApplicationService projectMemberService;
    @Autowired private PermissionService permissionService;

    @ParameterizedTest(name = "Workspace {0}: member=true, manage={1}")
    @MethodSource("workspaceRoleMatrix")
    void workspaceRoleMatrixAllowsEveryMemberToReadButOnlyOwnerAndAdminToManage(
            WorkspaceRole role, boolean canManage) {
        Fixture fixture = workspaceFixture(role);

        assertThat(permissionService.requireWorkspaceMember(fixture.workspaceId(), fixture.actorId()))
                .isEqualTo(role);
        assertThat(succeeds(() -> permissionService.requireWorkspaceMemberManager(
                fixture.workspaceId(), fixture.actorId()))).isEqualTo(canManage);
    }

    @ParameterizedTest(name = "Workspace {0}: regular={1}, owner={2}")
    @MethodSource("workspaceMutationMatrix")
    void workspaceMemberMutationMatrixProtectsOwnerRole(
            WorkspaceRole actorRole, boolean canAddRegularMember, boolean canAddOwner) {
        Fixture fixture = workspaceFixture(actorRole);
        UUID regularCandidate = activeUser(actorRole + "-regular@example.com", "Regular Candidate");
        UUID ownerCandidate = activeUser(actorRole + "-owner@example.com", "Owner Candidate");

        assertThat(succeeds(() -> workspaceMemberService.add(
                fixture.actorId(), fixture.workspaceId(), regularCandidate, WorkspaceRole.MEMBER)))
                .isEqualTo(canAddRegularMember);
        assertThat(succeeds(() -> workspaceMemberService.add(
                fixture.actorId(), fixture.workspaceId(), ownerCandidate, WorkspaceRole.OWNER)))
                .isEqualTo(canAddOwner);
    }

    @ParameterizedTest(name = "Project {0}: read={1}, manage={2}, write={3}")
    @MethodSource("projectRoleMatrix")
    void projectRoleMatrixCoversImplicitAndExplicitMembership(
            ProjectAccess access, boolean canRead, boolean canManage, boolean canWrite) {
        ProjectFixture fixture = projectFixture(access);

        assertThat(permissionService.canReadProject(fixture.projectId(), fixture.actorId()))
                .isEqualTo(canRead);
        assertThat(permissionService.canManageProjectMembers(fixture.projectId(), fixture.actorId()))
                .isEqualTo(canManage);
        assertThat(succeeds(() -> permissionService.requireIssueWriter(
                fixture.projectId(), fixture.actorId()))).isEqualTo(canWrite);
    }

    @ParameterizedTest(name = "Project member mutation by {0}")
    @EnumSource(ProjectAccess.class)
    void projectMemberMutationRequiresImplicitOrExplicitProjectAdmin(ProjectAccess access) {
        ProjectFixture fixture = projectFixture(access);
        UUID candidate = activeUser(access + "-candidate@example.com", "Project Candidate");
        workspaceMemberService.add(
                fixture.ownerId(), fixture.workspaceId(), candidate, WorkspaceRole.MEMBER);

        boolean expected = access == ProjectAccess.WORKSPACE_OWNER
                || access == ProjectAccess.WORKSPACE_ADMIN
                || access == ProjectAccess.PROJECT_ADMIN;
        assertThat(succeeds(() -> projectMemberService.add(
                fixture.actorId(), fixture.projectId(), candidate, ProjectRole.VIEWER)))
                .isEqualTo(expected);
    }

    private Fixture workspaceFixture(WorkspaceRole actorRole) {
        UUID ownerId = activeUser("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Permission Matrix", null)).id();
        if (actorRole == WorkspaceRole.OWNER) return new Fixture(ownerId, workspaceId, ownerId);
        UUID actorId = activeUser("actor-" + UUID.randomUUID() + "@example.com", actorRole.name());
        workspaceMemberService.add(ownerId, workspaceId, actorId, actorRole);
        return new Fixture(ownerId, workspaceId, actorId);
    }

    private ProjectFixture projectFixture(ProjectAccess access) {
        UUID ownerId = activeUser("project-owner-" + UUID.randomUUID() + "@example.com", "Owner");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Project Permission Matrix", null)).id();
        UUID projectId = projectService.create(
                ownerId, workspaceId, new CreateProjectRequest("Matrix Project", null)).id();
        if (access == ProjectAccess.WORKSPACE_OWNER) {
            return new ProjectFixture(ownerId, workspaceId, projectId, ownerId);
        }
        UUID actorId = activeUser("project-actor-" + UUID.randomUUID() + "@example.com", access.name());
        WorkspaceRole workspaceRole = access == ProjectAccess.WORKSPACE_ADMIN
                ? WorkspaceRole.ADMIN : WorkspaceRole.MEMBER;
        workspaceMemberService.add(ownerId, workspaceId, actorId, workspaceRole);
        if (access.projectRole != null) {
            projectMemberService.add(ownerId, projectId, actorId, access.projectRole);
        }
        return new ProjectFixture(ownerId, workspaceId, projectId, actorId);
    }

    private UUID activeUser(String email, String displayName) {
        UUID userId = UUID.randomUUID();
        projectionService.consume(new EventEnvelope<>(
                UUID.randomUUID(), "identity.user.registered", 1, Instant.now(),
                new EventScope(null, null, userId), userId,
                new IdentityUserEventPayload(
                        userId, email, displayName, AccountStatus.ACTIVE, 1),
                Map.of()));
        return userId;
    }

    private static boolean succeeds(Supplier<?> invocation) {
        Throwable failure = catchThrowable(invocation::get);
        if (failure == null) {
            return true;
        }
        assertThat(failure).isInstanceOf(BizException.class);
        return false;
    }

    private static Stream<Arguments> workspaceRoleMatrix() {
        return Stream.of(
                Arguments.of(WorkspaceRole.OWNER, true),
                Arguments.of(WorkspaceRole.ADMIN, true),
                Arguments.of(WorkspaceRole.MEMBER, false),
                Arguments.of(WorkspaceRole.VIEWER, false));
    }

    private static Stream<Arguments> workspaceMutationMatrix() {
        return Stream.of(
                Arguments.of(WorkspaceRole.OWNER, true, true),
                Arguments.of(WorkspaceRole.ADMIN, true, false),
                Arguments.of(WorkspaceRole.MEMBER, false, false),
                Arguments.of(WorkspaceRole.VIEWER, false, false));
    }

    private static Stream<Arguments> projectRoleMatrix() {
        return Stream.of(
                Arguments.of(ProjectAccess.WORKSPACE_OWNER, true, true, true),
                Arguments.of(ProjectAccess.WORKSPACE_ADMIN, true, true, true),
                Arguments.of(ProjectAccess.PROJECT_ADMIN, true, true, true),
                Arguments.of(ProjectAccess.MEMBER, true, false, true),
                Arguments.of(ProjectAccess.VIEWER, true, false, false),
                Arguments.of(ProjectAccess.WORKSPACE_ONLY, false, false, false));
    }

    private enum ProjectAccess {
        WORKSPACE_OWNER(null),
        WORKSPACE_ADMIN(null),
        PROJECT_ADMIN(ProjectRole.PROJECT_ADMIN),
        MEMBER(ProjectRole.MEMBER),
        VIEWER(ProjectRole.VIEWER),
        WORKSPACE_ONLY(null);

        private final ProjectRole projectRole;

        ProjectAccess(ProjectRole projectRole) {
            this.projectRole = projectRole;
        }
    }

    private record Fixture(UUID ownerId, UUID workspaceId, UUID actorId) {}
    private record ProjectFixture(UUID ownerId, UUID workspaceId, UUID projectId, UUID actorId) {}
}
