package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "user_directory")
public class UserDirectory {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "normalized_email", nullable = false, length = 320, unique = true)
    private String normalizedEmail;

    @Column(name = "email_display", nullable = false, length = 320)
    private String emailDisplay;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDirectory() {
    }

    public UserDirectory(
            UUID userId,
            String normalizedEmail,
            String emailDisplay,
            String displayName,
            AccountStatus accountStatus,
            long sourceVersion,
            Instant now) {
        this.userId = userId;
        this.normalizedEmail = normalizedEmail;
        this.emailDisplay = emailDisplay;
        this.displayName = displayName;
        this.accountStatus = accountStatus;
        this.sourceVersion = sourceVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean applySourceVersion(
            String normalizedEmail,
            String emailDisplay,
            String displayName,
            AccountStatus accountStatus,
            long sourceVersion,
            Instant now) {
        if (sourceVersion <= this.sourceVersion) {
            return false;
        }
        this.normalizedEmail = normalizedEmail;
        this.emailDisplay = emailDisplay;
        this.displayName = displayName;
        this.accountStatus = accountStatus;
        this.sourceVersion = sourceVersion;
        this.updatedAt = now;
        return true;
    }

    public void changeAvatar(UUID avatarFileId, Instant now) {
        this.avatarFileId = avatarFileId;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getEmailDisplay() {
        return emailDisplay;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UUID getAvatarFileId() {
        return avatarFileId;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
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
