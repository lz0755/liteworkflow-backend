package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.WorkspaceMember;
import com.liteworkflow.core.domain.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID userId,
        String displayName,
        String email,
        UUID avatarFileId,
        WorkspaceRole role,
        MemberStatus status,
        Instant joinedAt) {

    public static WorkspaceMemberResponse from(
            WorkspaceMember member, UserDirectory user, boolean revealEmail) {
        return new WorkspaceMemberResponse(
                user.getUserId(),
                user.getDisplayName(),
                revealEmail ? user.getEmailDisplay() : null,
                user.getAvatarFileId(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt());
    }
}
