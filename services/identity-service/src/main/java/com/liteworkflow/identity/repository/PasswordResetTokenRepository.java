package com.liteworkflow.identity.repository;

import com.liteworkflow.identity.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.id = :id
               and t.tokenHash = :tokenHash
               and t.usedAt is null
               and t.revokedAt is null
               and t.expiresAt > :now
            """)
    int claimForUse(@Param("id") UUID id, @Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.revokedAt = :now
             where t.userId = :userId and t.usedAt is null and t.revokedAt is null
            """)
    int revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
