package com.liteworkflow.identity.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.identity.domain.IdentityUser;
import com.liteworkflow.identity.infrastructure.EmailNormalizer;
import com.liteworkflow.identity.outbox.IdentityOutboxService;
import com.liteworkflow.identity.repository.IdentityUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityRegistrationTransaction {

    private final IdentityUserRepository userRepository;
    private final EmailNormalizer emailNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final IdentityOutboxService outboxService;
    private final Clock clock;

    IdentityRegistrationTransaction(
            IdentityUserRepository userRepository,
            EmailNormalizer emailNormalizer,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            IdentityOutboxService outboxService,
            Clock clock) {
        this.userRepository = userRepository;
        this.emailNormalizer = emailNormalizer;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public IdentityUser register(String email, String displayName, String password) {
        String normalizedEmail = emailNormalizer.normalize(email);
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedDisplayName.isBlank() || normalizedDisplayName.length() > 100) {
            throw new BizException(com.liteworkflow.common.core.error.CommonErrorCode.VALIDATION_ERROR,
                    "displayName must be between 1 and 100 characters");
        }
        passwordPolicy.validate(password);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BizException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        Instant now = clock.instant();
        IdentityUser user = IdentityUser.register(
                UUID.randomUUID(), normalizedEmail, normalizedDisplayName, passwordEncoder.encode(password), now);
        userRepository.saveAndFlush(user); // DB unique constraint is the concurrency authority.
        outboxService.enqueueUserEvent("identity.user.registered", user, user.getId());
        return user;
    }
}
