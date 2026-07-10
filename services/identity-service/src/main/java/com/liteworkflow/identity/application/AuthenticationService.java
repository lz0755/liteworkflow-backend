package com.liteworkflow.identity.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.identity.config.IdentityProperties;
import com.liteworkflow.identity.domain.IdentityStatus;
import com.liteworkflow.identity.domain.IdentityUser;
import com.liteworkflow.identity.domain.LoginOutcome;
import com.liteworkflow.identity.domain.PasswordResetToken;
import com.liteworkflow.identity.domain.RefreshToken;
import com.liteworkflow.identity.infrastructure.EmailNormalizer;
import com.liteworkflow.identity.infrastructure.LoginAttemptGuard;
import com.liteworkflow.identity.infrastructure.PasswordResetMailRequested;
import com.liteworkflow.identity.infrastructure.SecretTokenService;
import com.liteworkflow.identity.repository.IdentityUserRepository;
import com.liteworkflow.identity.repository.PasswordResetTokenRepository;
import com.liteworkflow.identity.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    // A valid BCrypt value keeps unknown-user login timing close to known-user login timing.
    private static final String DUMMY_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5xg6wM6B4UDdW8mKsqK9AhcNoIH9eZu";

    private final IdentityRegistrationService registrationService;
    private final IdentityUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final LoginAuditService loginAuditService;
    private final EmailNormalizer emailNormalizer;
    private final LoginAttemptGuard loginAttemptGuard;
    private final SecretTokenService secretTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtTokenService jwtTokenService;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public AuthenticationService(
            IdentityRegistrationService registrationService,
            IdentityUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            LoginAuditService loginAuditService,
            EmailNormalizer emailNormalizer,
            LoginAttemptGuard loginAttemptGuard,
            SecretTokenService secretTokens,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            JwtTokenService jwtTokenService,
            IdentityProperties properties,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.registrationService = registrationService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.loginAuditService = loginAuditService;
        this.emailNormalizer = emailNormalizer;
        this.loginAttemptGuard = loginAttemptGuard;
        this.secretTokens = secretTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    public TokenPair register(String email, String displayName, String password) {
        IdentityUser user = registrationService.register(email, displayName, password);
        return issueTokenPair(user, clock.instant());
    }

    @Transactional
    public TokenPair login(String email, String password, String clientIp) {
        String normalizedEmail = emailNormalizer.normalize(email);
        Instant now = clock.instant();
        String emailHash = secretTokens.hash(normalizedEmail);
        String ipHash = secretTokens.hash(safeIp(clientIp));
        if (loginAttemptGuard.isBlocked(normalizedEmail, clientIp)) {
            loginAuditService.recordRejected(null, emailHash, LoginOutcome.RATE_LIMITED, ipHash, now);
            throw new BizException(IdentityErrorCode.AUTHENTICATION_FAILED);
        }

        IdentityUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
        String candidateHash = user == null ? DUMMY_BCRYPT : user.getPasswordHash();
        boolean passwordMatches = password != null && passwordEncoder.matches(password, candidateHash);
        if (user == null || user.getStatus() != IdentityStatus.ACTIVE || !passwordMatches) {
            loginAttemptGuard.recordFailure(normalizedEmail, clientIp);
            loginAuditService.recordRejected(
                    user == null ? null : user.getId(), emailHash, LoginOutcome.FAILED, ipHash, now);
            throw new BizException(IdentityErrorCode.AUTHENTICATION_FAILED);
        }

        loginAttemptGuard.clear(normalizedEmail, clientIp);
        loginAuditService.recordSucceeded(user.getId(), emailHash, ipHash, now);
        return issueTokenPair(user, now);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        String tokenHash = secretTokens.hash(requireToken(rawRefreshToken, IdentityErrorCode.REFRESH_TOKEN_INVALID));
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BizException(IdentityErrorCode.REFRESH_TOKEN_INVALID));
        IdentityUser user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new BizException(IdentityErrorCode.REFRESH_TOKEN_INVALID));
        if (user.getStatus() != IdentityStatus.ACTIVE) {
            throw new BizException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }

        String nextRawToken = secretTokens.generate();
        UUID replacementId = UUID.randomUUID();
        RefreshToken replacement = new RefreshToken(
                replacementId,
                user.getId(),
                secretTokens.hash(nextRawToken),
                now.plus(properties.getRefreshTokenTtl()),
                now);
        refreshTokenRepository.save(replacement);
        if (refreshTokenRepository.consumeForRotation(current.getId(), tokenHash, replacementId, now) != 1) {
            // Transaction rollback removes the unconsumed replacement, preventing replay races from minting a token.
            throw new BizException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new TokenPair(issueAccessToken(user), nextRawToken, replacement.getExpiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = secretTokens.hash(requireToken(rawRefreshToken, IdentityErrorCode.REFRESH_TOKEN_INVALID));
        refreshTokenRepository.revokeByTokenHash(tokenHash, clock.instant());
    }

    @Transactional(readOnly = true)
    public IdentityUserView me(UUID userId) {
        return toView(requireActiveUser(userId));
    }

    @Transactional
    public TokenPair changePassword(UUID userId, String currentPassword, String newPassword) {
        IdentityUser user = requireActiveUserForUpdate(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BizException(IdentityErrorCode.CURRENT_PASSWORD_INVALID);
        }
        passwordPolicy.validate(newPassword);
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokenRepository.revokeAllForUser(userId, now);
        return issueTokenPair(user, now);
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        IdentityUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != IdentityStatus.ACTIVE) {
            return; // Same successful response for absent and disabled accounts.
        }
        Instant now = clock.instant();
        resetTokenRepository.revokeActiveForUser(user.getId(), now);
        String rawToken = secretTokens.generate();
        resetTokenRepository.save(new PasswordResetToken(
                UUID.randomUUID(),
                user.getId(),
                secretTokens.hash(rawToken),
                now.plus(properties.getResetTokenTtl()),
                now));
        // The opaque secret stays in-memory for the direct mail adapter; it is neither persisted nor put on MQ.
        applicationEventPublisher.publishEvent(new PasswordResetMailRequested(
                user.getId(), user.getEmail(), rawToken, now.plus(properties.getResetTokenTtl())));
    }

    @Transactional
    public void resetPassword(String rawResetToken, String newPassword) {
        String tokenHash = secretTokens.hash(requireToken(rawResetToken, IdentityErrorCode.RESET_TOKEN_INVALID));
        Instant now = clock.instant();
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BizException(IdentityErrorCode.RESET_TOKEN_INVALID));
        if (resetTokenRepository.claimForUse(resetToken.getId(), tokenHash, now) != 1) {
            throw new BizException(IdentityErrorCode.RESET_TOKEN_INVALID);
        }
        IdentityUser user = userRepository.findByIdForUpdate(resetToken.getUserId())
                .orElseThrow(() -> new BizException(IdentityErrorCode.RESET_TOKEN_INVALID));
        if (user.getStatus() != IdentityStatus.ACTIVE) {
            throw new BizException(IdentityErrorCode.RESET_TOKEN_INVALID);
        }
        passwordPolicy.validate(newPassword);
        user.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        resetTokenRepository.revokeActiveForUser(user.getId(), now);
    }

    private TokenPair issueTokenPair(IdentityUser user, Instant now) {
        String rawRefreshToken = secretTokens.generate();
        Instant refreshExpiresAt = now.plus(properties.getRefreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(), user.getId(), secretTokens.hash(rawRefreshToken), refreshExpiresAt, now));
        return new TokenPair(issueAccessToken(user), rawRefreshToken, refreshExpiresAt);
    }

    private String issueAccessToken(IdentityUser user) {
        return jwtTokenService.issueAccessToken(new CurrentUser(user.getId(), user.getEmail(), Set.of("USER")));
    }

    private IdentityUser requireActiveUser(UUID userId) {
        IdentityUser user = userRepository.findById(userId).orElseThrow(() -> new BizException(IdentityErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != IdentityStatus.ACTIVE) {
            throw new BizException(IdentityErrorCode.USER_DISABLED);
        }
        return user;
    }

    private IdentityUser requireActiveUserForUpdate(UUID userId) {
        IdentityUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException(IdentityErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != IdentityStatus.ACTIVE) {
            throw new BizException(IdentityErrorCode.USER_DISABLED);
        }
        return user;
    }

    private IdentityUserView toView(IdentityUser user) {
        return new IdentityUserView(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(), user.getSourceVersion());
    }

    private static String requireToken(String token, IdentityErrorCode errorCode) {
        if (token == null || token.isBlank() || token.length() > 512) {
            throw new BizException(errorCode);
        }
        return token;
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
