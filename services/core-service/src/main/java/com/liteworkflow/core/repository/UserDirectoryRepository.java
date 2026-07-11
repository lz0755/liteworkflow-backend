package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.UserDirectory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserDirectoryRepository extends JpaRepository<UserDirectory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserDirectory u where u.userId = :userId")
    Optional<UserDirectory> findByIdForUpdate(UUID userId);

    @Query("""
            select u from UserDirectory u
             where u.accountStatus = com.liteworkflow.core.domain.AccountStatus.ACTIVE
               and (lower(u.normalizedEmail) like :pattern or lower(u.displayName) like :pattern)
             order by lower(u.displayName), u.userId
            """)
    Page<UserDirectory> searchActive(String pattern, Pageable pageable);

    @Query("""
            select u from UserDirectory u
             where u.accountStatus = com.liteworkflow.core.domain.AccountStatus.ACTIVE
               and (lower(u.normalizedEmail) like :pattern or lower(u.displayName) like :pattern)
               and not exists (
                   select m.id from WorkspaceMember m
                    where m.workspaceId = :workspaceId
                      and m.userId = u.userId
                      and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               )
             order by lower(u.displayName), u.userId
            """)
    Page<UserDirectory> searchActiveExcludingWorkspaceMembers(
            String pattern, UUID workspaceId, Pageable pageable);
}
