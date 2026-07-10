package com.liteworkflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "identity_users")
public class IdentityUser {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdentityStatus status;

    /** Monotonic source version consumed by core.user_directory. */
    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdentityUser() {
    }

    private IdentityUser(
            UUID id,
            String email,
            String displayName,
            String passwordHash,
            IdentityStatus status,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.sourceVersion = 1;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static IdentityUser register(
            UUID id, String email, String displayName, String passwordHash, Instant now) {
        return new IdentityUser(id, email, displayName, passwordHash, IdentityStatus.ACTIVE, now);
    }

    /** Returns true when a directory-syncing field changed. */
    public boolean updateDirectoryFields(String newEmail, String newDisplayName, IdentityStatus newStatus, Instant now) {
        boolean changed = !email.equals(newEmail)
                || !displayName.equals(newDisplayName)
                || status != newStatus;
        if (changed) {
            email = newEmail;
            displayName = newDisplayName;
            status = newStatus;
            sourceVersion++;
            updatedAt = now;
        }
        return changed;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public IdentityStatus getStatus() {
        return status;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
