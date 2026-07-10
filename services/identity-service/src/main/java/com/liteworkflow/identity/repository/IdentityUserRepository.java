package com.liteworkflow.identity.repository;

import com.liteworkflow.identity.domain.IdentityUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, UUID> {

    Optional<IdentityUser> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from IdentityUser u where u.id = :id")
    Optional<IdentityUser> findByIdForUpdate(UUID id);
}
