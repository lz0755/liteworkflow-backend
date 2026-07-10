package com.liteworkflow.identity.application;

import com.liteworkflow.identity.domain.LoginLog;
import com.liteworkflow.identity.domain.LoginOutcome;
import com.liteworkflow.identity.repository.LoginLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAuditService {

    private final LoginLogRepository repository;

    public LoginAuditService(LoginLogRepository repository) {
        this.repository = repository;
    }

    /** Failure audit must survive the authentication transaction's expected rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(
            UUID userId, String emailHash, LoginOutcome outcome, String ipHash, Instant occurredAt) {
        if (outcome == LoginOutcome.SUCCEEDED) {
            throw new IllegalArgumentException("recordRejected does not accept a successful outcome");
        }
        save(userId, emailHash, outcome, ipHash, occurredAt);
    }

    /** A success is committed atomically with refresh-token issuance. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSucceeded(UUID userId, String emailHash, String ipHash, Instant occurredAt) {
        save(userId, emailHash, LoginOutcome.SUCCEEDED, ipHash, occurredAt);
    }

    private void save(UUID userId, String emailHash, LoginOutcome outcome, String ipHash, Instant occurredAt) {
        repository.save(new LoginLog(UUID.randomUUID(), userId, emailHash, outcome, ipHash, occurredAt));
    }
}
