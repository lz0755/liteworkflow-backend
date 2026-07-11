package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 500)
    private String bio;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 64)
    private String timezone;

    @Column(length = 32)
    private String locale;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String bio,
            String jobTitle,
            String timezone,
            String locale,
            UUID avatarFileId,
            Instant now) {
        this.bio = bio;
        this.jobTitle = jobTitle;
        this.timezone = timezone;
        this.locale = locale;
        this.avatarFileId = avatarFileId;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBio() {
        return bio;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public UUID getAvatarFileId() {
        return avatarFileId;
    }
}
