package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.Workspace;
import com.liteworkflow.core.domain.WorkspaceMember;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.response.WorkspaceMemberResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.outbox.WorkspaceMemberEventPayload;
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
public class WorkspaceMemberApplicationService {

    private final PermissionService permissionService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ActivityOutboxService activityOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public WorkspaceMemberApplicationService(
            PermissionService permissionService,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            UserDirectoryRepository userDirectoryRepository,
            ActivityOutboxService activityOutboxService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.permissionService = permissionService;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.activityOutboxService = activityOutboxService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<WorkspaceMemberResponse> list(UUID actorId, UUID workspaceId, int page, int size) {
        validatePage(page, size);
        WorkspaceRole actorRole = permissionService.requireWorkspaceMember(workspaceId, actorId);
        var members = memberRepository.findByWorkspaceIdAndStatusOrderByJoinedAtAscIdAsc(
                workspaceId, MemberStatus.ACTIVE, PageRequest.of(page - 1, size));
        Map<UUID, UserDirectory> users = userDirectoryRepository.findAllById(
                        members.getContent().stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserDirectory::getUserId, Function.identity()));
        var records = members.getContent().stream()
                .map(member -> WorkspaceMemberResponse.from(
                        member,
                        users.get(member.getUserId()),
                        actorRole.canManageMembers()))
                .toList();
        return PageResult.of(records, members.getTotalElements(), page, size);
    }

    @Transactional
    public WorkspaceMemberResponse add(
            UUID actorId, UUID workspaceId, UUID userId, WorkspaceRole requestedRole) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        lockWorkspace(workspaceId);
        WorkspaceRole actorRole = permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        requireOwnerForOwnerRole(actorRole, requestedRole);
        UserDirectory user = requireActiveCandidate(userId);
        Instant now = clock.instant();
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserIdForUpdate(workspaceId, userId)
                .orElse(null);
        if (member != null && member.getStatus() == MemberStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_ALREADY_EXISTS);
        }
        if (member == null) {
            member = new WorkspaceMember(
                    UUID.randomUUID(), workspaceId, userId, requestedRole, actorId, now);
        } else {
            member.reactivate(requestedRole, actorId, now);
        }
        memberRepository.saveAndFlush(member);
        activityOutboxService.recordWorkspaceMemberChange(
                "workspace.member.added",
                workspaceId,
                member.getId(),
                actorId,
                new WorkspaceMemberEventPayload(userId, requestedRole, null));
        applicationEventPublisher.publishEvent(new WorkspacePermissionInvalidation(workspaceId, userId));
        return WorkspaceMemberResponse.from(member, user, true);
    }

    @Transactional
    public WorkspaceMemberResponse changeRole(
            UUID actorId, UUID workspaceId, UUID userId, WorkspaceRole requestedRole) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        lockWorkspace(workspaceId);
        WorkspaceRole actorRole = permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        WorkspaceMember member = requireActiveMemberForUpdate(workspaceId, userId);
        if (member.getRole() == requestedRole) {
            return WorkspaceMemberResponse.from(member, requireUser(userId), true);
        }
        requireOwnerForOwnerChange(actorRole, member.getRole(), requestedRole);
        if (member.getRole() == WorkspaceRole.OWNER && requestedRole != WorkspaceRole.OWNER) {
            requireAnotherOwner(workspaceId);
        }
        WorkspaceRole previousRole = member.getRole();
        member.changeRole(requestedRole, clock.instant());
        activityOutboxService.recordWorkspaceMemberChange(
                "workspace.member.role.changed",
                workspaceId,
                member.getId(),
                actorId,
                new WorkspaceMemberEventPayload(userId, requestedRole, previousRole));
        applicationEventPublisher.publishEvent(new WorkspacePermissionInvalidation(workspaceId, userId));
        return WorkspaceMemberResponse.from(member, requireUser(userId), true);
    }

    @Transactional
    public void remove(UUID actorId, UUID workspaceId, UUID userId) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        lockWorkspace(workspaceId);
        WorkspaceRole actorRole = permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        WorkspaceMember member = requireActiveMemberForUpdate(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.OWNER) {
            requireOwnerForOwnerChange(actorRole, member.getRole(), null);
            requireAnotherOwner(workspaceId);
        }
        WorkspaceRole previousRole = member.getRole();
        member.remove(clock.instant());
        activityOutboxService.recordWorkspaceMemberChange(
                "workspace.member.removed",
                workspaceId,
                member.getId(),
                actorId,
                new WorkspaceMemberEventPayload(userId, null, previousRole));
        applicationEventPublisher.publishEvent(new WorkspacePermissionInvalidation(workspaceId, userId));
    }

    private Workspace lockWorkspace(UUID workspaceId) {
        return workspaceRepository.findActiveByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
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

    private WorkspaceMember requireActiveMemberForUpdate(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserIdForUpdate(workspaceId, userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private void requireOwnerForOwnerRole(WorkspaceRole actorRole, WorkspaceRole requestedRole) {
        if (requestedRole == WorkspaceRole.OWNER && actorRole != WorkspaceRole.OWNER) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED);
        }
    }

    private void requireOwnerForOwnerChange(
            WorkspaceRole actorRole, WorkspaceRole previousRole, WorkspaceRole requestedRole) {
        if ((previousRole == WorkspaceRole.OWNER || requestedRole == WorkspaceRole.OWNER)
                && actorRole != WorkspaceRole.OWNER) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED);
        }
    }

    private void requireAnotherOwner(UUID workspaceId) {
        long ownerCount = memberRepository.countByWorkspaceIdAndStatusAndRole(
                workspaceId, MemberStatus.ACTIVE, WorkspaceRole.OWNER);
        if (ownerCount <= 1) {
            throw new BizException(CoreErrorCode.WORKSPACE_LAST_OWNER_REQUIRED);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
