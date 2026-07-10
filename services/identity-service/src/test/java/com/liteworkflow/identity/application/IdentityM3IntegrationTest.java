package com.liteworkflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.identity.domain.IdentityStatus;
import com.liteworkflow.identity.domain.LocalOutboxEvent;
import com.liteworkflow.identity.domain.OutboxStatus;
import com.liteworkflow.identity.domain.PasswordResetToken;
import com.liteworkflow.identity.infrastructure.SecretTokenService;
import com.liteworkflow.identity.infrastructure.LoginAttemptGuard;
import com.liteworkflow.identity.outbox.IdentityEventPublisher;
import com.liteworkflow.identity.outbox.OutboxDispatchService;
import com.liteworkflow.identity.repository.IdentityUserRepository;
import com.liteworkflow.identity.repository.LocalOutboxEventRepository;
import com.liteworkflow.identity.repository.LoginLogRepository;
import com.liteworkflow.identity.repository.PasswordResetTokenRepository;
import com.liteworkflow.identity.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class IdentityM3IntegrationTest {

    private static final String PASSWORD = "Correct horse battery staple";

    @Autowired private AuthenticationService authenticationService;
    @Autowired private IdentityDirectoryMutationService directoryMutationService;
    @Autowired private IdentityUserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private LoginLogRepository loginLogRepository;
    @Autowired private LocalOutboxEventRepository outboxRepository;
    @Autowired private SecretTokenService secretTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxDispatchService dispatchService;
    @Autowired private MockMvc mockMvc;

    @MockBean private IdentityEventPublisher publisher;
    @MockBean private LoginAttemptGuard loginAttemptGuard;
    @MockBean private JavaMailSender mailSender;

    @BeforeEach
    void resetState() {
        reset(publisher);
        reset(loginAttemptGuard, mailSender);
        outboxRepository.deleteAll();
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentNormalizedRegistrationHasOneUserAndOneRegisteredEvent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> attempts = IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        try {
                            authenticationService.register(" Alice@Example.COM ", "Alice " + index, PASSWORD);
                            return null;
                        } catch (Throwable throwable) {
                            return throwable;
                        }
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> attempt : attempts) {
                outcomes.add(attempt.get(15, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull)
                    .allSatisfy(error -> assertThat(error).isInstanceOf(BizException.class));
            assertThat(userRepository.findAll()).hasSize(1);
            assertThat(userRepository.findAll().getFirst().getEmail()).isEqualTo("alice@example.com");
            assertThat(outboxRepository.findAll()).hasSize(1);
            assertThat(outboxRepository.findAll().getFirst().getEventType()).isEqualTo("identity.user.registered");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refreshRotationRejectsReplayAndDoesNotMintAnotherToken() {
        TokenPair initial = authenticationService.register("rotate@example.com", "Rotate", PASSWORD);

        TokenPair rotated = authenticationService.refresh(initial.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
        assertThatThrownBy(() -> authenticationService.refresh(initial.refreshToken()))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(authenticationService.refresh(rotated.refreshToken()).refreshToken()).isNotBlank();
        assertThat(refreshTokenRepository.findByTokenHash(initial.refreshToken())).isEmpty();
    }

    @Test
    void successfulLoginWorksThroughHttpAndCommitsAuditLog(CapturedOutput output) throws Exception {
        String registration = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "http-login@example.com",
                "displayName", "HTTP Login",
                "password", PASSWORD));
        String registrationResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String login = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "HTTP-LOGIN@example.com",
                "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode registrationJson = objectMapper.readTree(registrationResponse).path("data");
        JsonNode loginJson = objectMapper.readTree(loginResponse).path("data");
        assertThat(output.getAll()).doesNotContain(
                PASSWORD.substring(0, 20),
                secretPrefix(registrationJson.path("accessToken").asText()),
                secretPrefix(registrationJson.path("refreshToken").asText()),
                secretPrefix(loginJson.path("accessToken").asText()),
                secretPrefix(loginJson.path("refreshToken").asText()));

        assertThat(loginLogRepository.findAll()).singleElement()
                .extracting(com.liteworkflow.identity.domain.LoginLog::getOutcome)
                .isEqualTo(com.liteworkflow.identity.domain.LoginOutcome.SUCCEEDED);
    }

    @Test
    void failedAndRateLimitedLoginAuditsSurviveAuthenticationRollback() {
        authenticationService.register("audit@example.com", "Audit", PASSWORD);

        assertAuthenticationFailed(() -> authenticationService.login(
                "AUDIT@example.com", "wrong password", "192.0.2.10"));
        when(loginAttemptGuard.isBlocked("missing@example.com", "192.0.2.11")).thenReturn(true);
        assertAuthenticationFailed(() -> authenticationService.login(
                "missing@example.com", "wrong password", "192.0.2.11"));

        assertThat(loginLogRepository.findAll())
                .extracting(com.liteworkflow.identity.domain.LoginLog::getOutcome)
                .containsExactlyInAnyOrder(
                        com.liteworkflow.identity.domain.LoginOutcome.FAILED,
                        com.liteworkflow.identity.domain.LoginOutcome.RATE_LIMITED);
    }

    @Test
    void passwordResetTokenCanOnlyBeClaimedOnce() {
        authenticationService.register("reset@example.com", "Reset", PASSWORD);
        var user = userRepository.findByEmail("reset@example.com").orElseThrow();
        String rawToken = secretTokens.generate();
        Instant now = clock.instant();
        resetTokenRepository.saveAndFlush(new PasswordResetToken(
                java.util.UUID.randomUUID(),
                user.getId(),
                secretTokens.hash(rawToken),
                now.plusSeconds(300),
                now));

        authenticationService.resetPassword(rawToken, "A new correct password");

        assertThat(passwordEncoder.matches("A new correct password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThatThrownBy(() -> authenticationService.resetPassword(rawToken, "Another valid password"))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(IdentityErrorCode.RESET_TOKEN_INVALID);
    }

    @Test
    void forgotPasswordSendsOneTimeLinkAfterCommitWithoutPersistingRawToken() {
        authenticationService.register("mail@example.com", "Mail", PASSWORD);

        authenticationService.forgotPassword("MAIL@example.com");

        org.mockito.ArgumentCaptor<SimpleMailMessage> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly("mail@example.com");
        assertThat(message.getText()).contains("http://localhost:3000/reset-password?token=");
        assertThat(resetTokenRepository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.getTokenHash()).hasSize(64);
            assertThat(message.getText()).doesNotContain(stored.getTokenHash());
        });
    }

    @Test
    void failedMqDeliveryRemainsRecoverableAndLaterPublishes() throws Exception {
        authenticationService.register("outbox@example.com", "Outbox", PASSWORD);
        LocalOutboxEvent event = outboxRepository.findAll().getFirst();
        doThrow(new IllegalStateException("broker down")).doNothing().when(publisher).publish(any());

        dispatchService.dispatch(event.getId());
        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);

        Thread.sleep(20);
        dispatchService.recoverPending();

        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void userEventsIncrementVersionAndNeverContainCredentialsOrAuditFields() throws Exception {
        authenticationService.register("events@example.com", "Events", PASSWORD);
        var user = userRepository.findByEmail("events@example.com").orElseThrow();
        directoryMutationService.updateSelf(user.getId(), "EVENTS@example.com", "Events Updated");
        directoryMutationService.update(
                user.getId(), "events@example.com", "Events Updated", IdentityStatus.DISABLED, user.getId());

        List<JsonNode> events = outboxRepository.findAll().stream()
                .map(event -> readTree(event.getPayloadJson()))
                .sorted(Comparator.comparingLong(event -> event.path("payload").path("version").asLong()))
                .toList();
        assertThat(events).extracting(event -> event.path("eventType").asText())
                .containsExactly("identity.user.registered", "identity.user.updated", "identity.user.disabled");
        assertThat(events).extracting(event -> event.path("payload").path("version").asLong())
                .containsExactly(1L, 2L, 3L);
        assertThat(events).allSatisfy(event -> {
            String json = event.toString().toLowerCase();
            assertThat(json).doesNotContain("password", "refresh", "reset", "token_hash", "ip_hash", "email_hash");
        });
    }

    @Test
    void passwordsAndOpaqueTokensNeverEnterApplicationLogs(CapturedOutput output) {
        TokenPair initial = authenticationService.register("log-secret@example.com", "Log Secret", PASSWORD);
        TokenPair rotated = authenticationService.refresh(initial.refreshToken());

        assertThat(output.getAll())
                .doesNotContain(PASSWORD, initial.refreshToken(), rotated.refreshToken(), initial.accessToken());
    }

    private void assertAuthenticationFailed(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(IdentityErrorCode.AUTHENTICATION_FAILED);
    }

    private String secretPrefix(String secret) {
        return secret.substring(0, Math.min(secret.length(), 24));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
