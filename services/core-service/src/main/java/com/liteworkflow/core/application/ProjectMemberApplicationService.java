package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.domain.ProjectMember;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.Workspace;
import com.liteworkflow.core.dto.response.ProjectMemberResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.outbox.ProjectMemberEventPayload;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberApplicationService {

    private final PermissionService permissionService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ActivityOutboxService activityOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public ProjectMemberApplicationService(
            PermissionService permissionService,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserDirectoryRepository userDirectoryRepository,
            ActivityOutboxService activityOutboxService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.permissionService = permissionService;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.activityOutboxService = activityOutboxService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<ProjectMemberResponse> list(UUID actorId, UUID projectId, int page, int size) {
        validatePage(page, size);
        ProjectRole actorRole = permissionService.requireProjectMember(projectId, actorId);
        var members = memberRepository.findByProjectIdAndStatusOrderByJoinedAtAscIdAsc(
                projectId, MemberStatus.ACTIVE, PageRequest.of(page - 1, size));
        Map<UUID, UserDirectory> users = userDirectoryRepository.findAllById(
                        members.getContent().stream().map(ProjectMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserDirectory::getUserId, Function.identity()));
        var records = members.getContent().stream()
                .map(member -> ProjectMemberResponse.from(
                        member, users.get(member.getUserId()), actorRole.canManageMembers()))
                .toList();
        return PageResult.of(records, members.getTotalElements(), page, size);
    }

    @Transactional
    public ProjectMemberResponse add(UUID actorId, UUID projectId, UUID userId, ProjectRole requestedRole) {
        Project project = prepareManagedProject(actorId, projectId);
        UserDirectory user = requireActiveCandidate(userId);
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndStatus(
                project.getWorkspaceId(), userId, MemberStatus.ACTIVE)) {
            throw new BizException(CoreErrorCode.PROJECT_MEMBER_REQUIRES_WORKSPACE_MEMBER);
        }
        Instant now = clock.instant();
        ProjectMember member = memberRepository.findByProjectIdAndUserIdForUpdate(projectId, userId)
                .orElse(null);
        if (member != null && member.getStatus() == MemberStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }
        if (member == null) {
            member = new ProjectMember(UUID.randomUUID(), projectId, userId, requestedRole, actorId, now);
        } else {
            member.reactivate(requestedRole, actorId, now);
        }
        memberRepository.saveAndFlush(member);
        recordChange(
                "project.member.added", project, member, actorId,
                new ProjectMemberEventPayload(userId, requestedRole, null));
        return ProjectMemberResponse.from(member, user, true);
    }

    @Transactional
    public ProjectMemberResponse changeRole(
            UUID actorId, UUID projectId, UUID userId, ProjectRole requestedRole) {
        Project project = prepareManagedProject(actorId, projectId);
        ProjectMember member = requireActiveMemberForUpdate(projectId, userId);
        if (member.getRole() == requestedRole) {
            return ProjectMemberResponse.from(member, requireUser(userId), true);
        }
        if (member.getRole() == ProjectRole.PROJECT_ADMIN && requestedRole != ProjectRole.PROJECT_ADMIN) {
            requireAnotherAdmin(projectId);
        }
        ProjectRole previousRole = member.getRole();
        member.changeRole(requestedRole, clock.instant());
        recordChange(
                "project.member.role.changed", project, member, actorId,
                new ProjectMemberEventPayload(userId, requestedRole, previousRole));
        return ProjectMemberResponse.from(member, requireUser(userId), true);
    }

    @Transactional
    public void remove(UUID actorId, UUID projectId, UUID userId) {
        Project project = prepareManagedProject(actorId, projectId);
        ProjectMember member = requireActiveMemberForUpdate(projectId, userId);
        if (member.getRole() == ProjectRole.PROJECT_ADMIN) {
            requireAnotherAdmin(projectId);
        }
        ProjectRole previousRole = member.getRole();
        member.remove(clock.instant());
        recordChange(
                "project.member.removed", project, member, actorId,
                new ProjectMemberEventPayload(userId, null, previousRole));
    }

    private Project prepareManagedProject(UUID actorId, UUID projectId) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project existing = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        lockWorkspace(existing.getWorkspaceId());
        Project project = projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        permissionService.requireProjectMemberManager(projectId, actorId);
        return project;
    }

    private Workspace lockWorkspace(UUID workspaceId) {
        return workspaceRepository.findActiveByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
    }

    private ProjectMember requireActiveMemberForUpdate(UUID projectId, UUID userId) {
        ProjectMember member = memberRepository.findByProjectIdAndUserIdForUpdate(projectId, userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private UserDirectory requireActiveCandidate(UUID userId) {
        UserDirectory user = requireUser(userId);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.USER_NOT_ACTIVE);
        }
        return user;
    }

    private UserDirectory requireUser(UUID userId) {
        return userDirectoryRepository.findById(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
    }

    private void requireAnotherAdmin(UUID projectId) {
        long adminCount = memberRepository.countByProjectIdAndStatusAndRole(
                projectId, MemberStatus.ACTIVE, ProjectRole.PROJECT_ADMIN);
        if (adminCount <= 1) {
            throw new BizException(CoreErrorCode.PROJECT_LAST_ADMIN_REQUIRED);
        }
    }

    private void recordChange(
            String eventType,
            Project project,
            ProjectMember member,
            UUID actorId,
            ProjectMemberEventPayload payload) {
        activityOutboxService.recordProjectMemberChange(
                eventType,
                project.getWorkspaceId(),
                project.getId(),
                member.getId(),
                actorId,
                payload);
        applicationEventPublisher.publishEvent(
                new ProjectPermissionInvalidation(project.getId(), member.getUserId()));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
