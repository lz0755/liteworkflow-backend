package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.UserProfile;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String displayName,
        AccountStatus status,
        UUID avatarFileId,
        String bio,
        String jobTitle,
        String timezone,
        String locale) {

    public static UserProfileResponse from(UserDirectory user, UserProfile profile) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getEmailDisplay(),
                user.getDisplayName(),
                user.getAccountStatus(),
                profile.getAvatarFileId(),
                profile.getBio(),
                profile.getJobTitle(),
                profile.getTimezone(),
                profile.getLocale());
    }
}
