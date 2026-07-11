package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.Workspace;
import com.liteworkflow.core.domain.WorkspaceMember;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.dto.request.UpdateWorkspaceRequest;
import com.liteworkflow.core.dto.response.WorkspaceResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.outbox.WorkspaceMemberEventPayload;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceApplicationService {

    private final PermissionService permissionService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ActivityOutboxService activityOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public WorkspaceApplicationService(
            PermissionService permissionService,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            ActivityOutboxService activityOutboxService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.permissionService = permissionService;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.activityOutboxService = activityOutboxService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceResponse create(UUID actorId, CreateWorkspaceRequest request) {
        permissionService.requireActiveUser(actorId);
        Instant now = clock.instant();
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(), normalizeName(request.name()), normalizeDescription(request.description()), actorId, now));
        WorkspaceMember owner = memberRepository.save(new WorkspaceMember(
                UUID.randomUUID(), workspace.getId(), actorId, WorkspaceRole.OWNER, actorId, now));
        activityOutboxService.recordWorkspaceMemberChange(
                "workspace.member.added",
                workspace.getId(),
                owner.getId(),
                actorId,
                new WorkspaceMemberEventPayload(actorId, WorkspaceRole.OWNER, null));
        applicationEventPublisher.publishEvent(new WorkspacePermissionInvalidation(workspace.getId(), actorId));
        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResult<WorkspaceResponse> list(UUID actorId, int page, int size) {
        permissionService.requireActiveUser(actorId);
        validatePage(page, size);
        var workspaces = workspaceRepository.findAccessible(actorId, PageRequest.of(page - 1, size));
        List<WorkspaceResponse> records = workspaces.getContent().stream()
                .map(workspace -> WorkspaceResponse.from(
                        workspace, permissionService.requireWorkspaceMember(workspace.getId(), actorId)))
                .toList();
        return PageResult.of(records, workspaces.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID actorId, UUID workspaceId) {
        WorkspaceRole role = permissionService.requireWorkspaceMember(workspaceId, actorId);
        Workspace workspace = workspaceRepository.findActiveById(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
        return WorkspaceResponse.from(workspace, role);
    }

    @Transactional
    public WorkspaceResponse update(UUID actorId, UUID workspaceId, UpdateWorkspaceRequest request) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        Workspace workspace = workspaceRepository.findActiveByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
        // Recheck after acquiring the workspace lock in case the actor role changed while waiting.
        WorkspaceRole role = permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        String name = request.name() == null ? workspace.getName() : normalizeName(request.name());
        String description = request.description() == null
                ? workspace.getDescription()
                : normalizeDescription(request.description());
        workspace.update(name, description, clock.instant());
        return WorkspaceResponse.from(workspace, role);
    }

    @Transactional
    public void delete(UUID actorId, UUID workspaceId) {
        permissionService.requireWorkspaceRole(workspaceId, actorId, WorkspaceRole.OWNER);
        Workspace workspace = workspaceRepository.findActiveByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
        permissionService.requireWorkspaceRole(workspaceId, actorId, WorkspaceRole.OWNER);
        List<UUID> affectedUsers = memberRepository.findActiveUserIdsByWorkspaceId(workspaceId);
        workspace.delete(clock.instant());
        affectedUsers.forEach(userId -> applicationEventPublisher.publishEvent(
                new WorkspacePermissionInvalidation(workspaceId, userId)));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Workspace name is required");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
