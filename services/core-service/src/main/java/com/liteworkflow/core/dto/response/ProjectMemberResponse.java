package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.ProjectMember;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.UserDirectory;
import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID userId,
        String displayName,
        String email,
        UUID avatarFileId,
        ProjectRole role,
        MemberStatus status,
        Instant joinedAt) {

    public static ProjectMemberResponse from(
            ProjectMember member, UserDirectory user, boolean revealEmail) {
        return new ProjectMemberResponse(
                user.getUserId(),
                user.getDisplayName(),
                revealEmail ? user.getEmailDisplay() : null,
                user.getAvatarFileId(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt());
    }
}
