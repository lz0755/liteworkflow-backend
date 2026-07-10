package com.liteworkflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "login_logs")
public class LoginLog {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    /** SHA-256 of the normalized login identifier; the raw identifier is not an audit field. */
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoginOutcome outcome;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginLog() {
    }

    public LoginLog(UUID id, UUID userId, String emailHash, LoginOutcome outcome, String ipHash, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.emailHash = emailHash;
        this.outcome = outcome;
        this.ipHash = ipHash;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public String getIpHash() {
        return ipHash;
    }
}
