package com.liteworkflow.core.directory;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.core.application.WorkspacePermissionInvalidation;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.ConsumedEvent;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.UserProfile;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectoryProjectionService {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "identity.user.registered", "identity.user.updated", "identity.user.disabled");

    private final UserDirectoryRepository userDirectoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public UserDirectoryProjectionService(
            UserDirectoryRepository userDirectoryRepository,
            UserProfileRepository userProfileRepository,
            ConsumedEventRepository consumedEventRepository,
            WorkspaceMemberRepository memberRepository,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.userProfileRepository = userProfileRepository;
        this.consumedEventRepository = consumedEventRepository;
        this.memberRepository = memberRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    /** Event id de-duplication and source-version projection update commit atomically. */
    @Transactional
    public boolean consume(EventEnvelope<IdentityUserEventPayload> envelope) {
        validate(envelope);
        if (consumedEventRepository.existsById(envelope.eventId())) {
            return false;
        }

        Instant now = clock.instant();
        IdentityUserEventPayload payload = envelope.payload();
        String normalizedEmail = normalizeEmail(payload.email());
        String displayName = normalizeDisplayName(payload.displayName());
        UserDirectory user = userDirectoryRepository.findByIdForUpdate(payload.userId()).orElse(null);
        boolean applied;
        if (user == null) {
            user = new UserDirectory(
                    payload.userId(),
                    normalizedEmail,
                    payload.email().trim(),
                    displayName,
                    payload.status(),
                    payload.version(),
                    now);
            userDirectoryRepository.save(user);
            applied = true;
        } else {
            applied = user.applySourceVersion(
                    normalizedEmail,
                    payload.email().trim(),
                    displayName,
                    payload.status(),
                    payload.version(),
                    now);
        }

        if (!userProfileRepository.existsById(payload.userId())) {
            userProfileRepository.save(new UserProfile(payload.userId(), now));
        }
        consumedEventRepository.save(new ConsumedEvent(envelope.eventId(), envelope.eventType(), now));

        if (applied && payload.status() != AccountStatus.ACTIVE) {
            memberRepository.findActiveWorkspaceIdsByUserId(payload.userId()).forEach(workspaceId ->
                    applicationEventPublisher.publishEvent(
                            new WorkspacePermissionInvalidation(workspaceId, payload.userId())));
        }
        return applied;
    }

    private void validate(EventEnvelope<IdentityUserEventPayload> envelope) {
        if (!SUPPORTED_EVENT_TYPES.contains(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported identity user event type");
        }
        IdentityUserEventPayload payload = envelope.payload();
        if (!envelope.aggregateId().equals(payload.userId())) {
            throw new IllegalArgumentException("Identity user event aggregate does not match payload");
        }
        if (payload.version() < 1) {
            throw new IllegalArgumentException("Identity source version must be positive");
        }
        if (payload.status() == null || payload.email() == null || payload.displayName() == null) {
            throw new IllegalArgumentException("Identity user event is missing directory fields");
        }
    }

    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalArgumentException("Identity user event email is invalid");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = displayName.trim();
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("Identity user event display name is invalid");
        }
        return normalized;
    }
}
