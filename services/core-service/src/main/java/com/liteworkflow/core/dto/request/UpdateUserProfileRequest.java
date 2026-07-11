package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUserProfileRequest(
        @Size(max = 500) String bio,
        @Size(max = 120) String jobTitle,
        @Size(max = 64) String timezone,
        @Size(max = 32) String locale,
        UUID avatarFileId) {
}
