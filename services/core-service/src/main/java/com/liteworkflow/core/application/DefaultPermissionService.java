package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPermissionService implements PermissionService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspacePermissionCache permissionCache;

    public DefaultPermissionService(
            UserDirectoryRepository userDirectoryRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspacePermissionCache permissionCache) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.memberRepository = memberRepository;
        this.permissionCache = permissionCache;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveUser(UUID userId) {
        UserDirectory user = userDirectoryRepository.findById(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.USER_NOT_ACTIVE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole requireWorkspaceMember(UUID workspaceId, UUID userId) {
        requireActiveUser(userId);
        // The database remains authoritative. A failed Redis eviction must never preserve access.
        WorkspaceRole role = memberRepository.findActiveRole(workspaceId, userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_PERMISSION_DENIED));
        permissionCache.put(workspaceId, userId, role);
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole requireWorkspaceRole(UUID workspaceId, UUID userId, WorkspaceRole... roles) {
        WorkspaceRole currentRole = requireWorkspaceMember(workspaceId, userId);
        if (Arrays.stream(roles).noneMatch(currentRole::equals)) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED);
        }
        return currentRole;
    }
}
