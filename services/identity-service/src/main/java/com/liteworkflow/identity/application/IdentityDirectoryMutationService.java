package com.liteworkflow.identity.application;

import com.liteworkflow.identity.domain.IdentityStatus;
import com.liteworkflow.identity.domain.IdentityUser;
import com.liteworkflow.identity.infrastructure.EmailNormalizer;
import com.liteworkflow.identity.outbox.IdentityOutboxService;
import com.liteworkflow.identity.repository.IdentityUserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit mutation seam for self-service profile changes and future account administration. */
@Service
public class IdentityDirectoryMutationService {

    private final IdentityUserRepository userRepository;
    private final EmailNormalizer emailNormalizer;
    private final IdentityOutboxService outboxService;
    private final Clock clock;

    public IdentityDirectoryMutationService(
            IdentityUserRepository userRepository,
            EmailNormalizer emailNormalizer,
            IdentityOutboxService outboxService,
            Clock clock) {
        this.userRepository = userRepository;
        this.emailNormalizer = emailNormalizer;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public IdentityUserView update(UUID userId, String email, String displayName, IdentityStatus status, UUID actorId) {
        IdentityUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new com.liteworkflow.common.core.error.BizException(IdentityErrorCode.USER_NOT_FOUND));
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedDisplayName.isBlank() || normalizedDisplayName.length() > 100) {
            throw new com.liteworkflow.common.core.error.BizException(
                    com.liteworkflow.common.core.error.CommonErrorCode.VALIDATION_ERROR,
                    "displayName must be between 1 and 100 characters");
        }
        IdentityStatus previousStatus = user.getStatus();
        if (user.updateDirectoryFields(emailNormalizer.normalize(email), normalizedDisplayName, status, clock.instant())) {
            String eventType = previousStatus != IdentityStatus.DISABLED && status == IdentityStatus.DISABLED
                    ? "identity.user.disabled"
                    : "identity.user.updated";
            outboxService.enqueueUserEvent(eventType, user, actorId);
        }
        return new IdentityUserView(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(), user.getSourceVersion());
    }

    @Transactional
    public IdentityUserView updateSelf(UUID userId, String email, String displayName) {
        IdentityUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new com.liteworkflow.common.core.error.BizException(IdentityErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != IdentityStatus.ACTIVE) {
            throw new com.liteworkflow.common.core.error.BizException(IdentityErrorCode.USER_DISABLED);
        }
        return update(userId, email, displayName, user.getStatus(), userId);
    }
}
