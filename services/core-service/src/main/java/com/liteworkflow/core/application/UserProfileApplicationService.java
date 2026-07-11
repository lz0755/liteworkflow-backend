package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.UserProfile;
import com.liteworkflow.core.dto.request.UpdateUserProfileRequest;
import com.liteworkflow.core.dto.response.UserProfileResponse;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileApplicationService {

    private final PermissionService permissionService;
    private final UserDirectoryRepository userDirectoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;

    public UserProfileApplicationService(
            PermissionService permissionService,
            UserDirectoryRepository userDirectoryRepository,
            UserProfileRepository userProfileRepository,
            Clock clock) {
        this.permissionService = permissionService;
        this.userDirectoryRepository = userDirectoryRepository;
        this.userProfileRepository = userProfileRepository;
        this.clock = clock;
    }

    @Transactional
    public UserProfileResponse get(UUID userId) {
        permissionService.requireActiveUser(userId);
        UserDirectory user = userDirectoryRepository.findById(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> userProfileRepository.save(new UserProfile(userId, clock.instant())));
        return UserProfileResponse.from(user, profile);
    }

    @Transactional
    public UserProfileResponse update(UUID userId, UpdateUserProfileRequest request) {
        permissionService.requireActiveUser(userId);
        UserDirectory user = userDirectoryRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> new UserProfile(userId, clock.instant()));

        String timezone = request.timezone() == null ? profile.getTimezone() : normalize(request.timezone());
        String locale = request.locale() == null ? profile.getLocale() : normalize(request.locale());
        validateTimezone(timezone);
        validateLocale(locale);
        String bio = request.bio() == null ? profile.getBio() : normalize(request.bio());
        String jobTitle = request.jobTitle() == null ? profile.getJobTitle() : normalize(request.jobTitle());
        UUID avatarFileId = request.avatarFileId() == null ? profile.getAvatarFileId() : request.avatarFileId();

        profile.update(bio, jobTitle, timezone, locale, avatarFileId, clock.instant());
        user.changeAvatar(avatarFileId, clock.instant());
        userProfileRepository.save(profile);
        return UserProfileResponse.from(user, profile);
    }

    private String normalize(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateTimezone(String timezone) {
        if (timezone == null) {
            return;
        }
        try {
            ZoneId.of(timezone);
        } catch (ZoneRulesException exception) {
            throw new BizException(CoreErrorCode.INVALID_PROFILE, "Timezone is invalid");
        }
    }

    private void validateLocale(String locale) {
        if (locale != null && Locale.forLanguageTag(locale).getLanguage().isBlank()) {
            throw new BizException(CoreErrorCode.INVALID_PROFILE, "Locale is invalid");
        }
    }
}
