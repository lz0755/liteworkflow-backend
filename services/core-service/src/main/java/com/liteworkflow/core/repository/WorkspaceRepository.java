package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Query("""
            select w from Workspace w, WorkspaceMember m
             where w.id = m.workspaceId
               and w.status = com.liteworkflow.core.domain.WorkspaceStatus.ACTIVE
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and m.userId = :userId
             order by lower(w.name), w.id
            """)
    Page<Workspace> findAccessible(UUID userId, Pageable pageable);

    @Query("""
            select w from Workspace w
             where w.id = :workspaceId
               and w.status = com.liteworkflow.core.domain.WorkspaceStatus.ACTIVE
            """)
    Optional<Workspace> findActiveById(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from Workspace w
             where w.id = :workspaceId
               and w.status = com.liteworkflow.core.domain.WorkspaceStatus.ACTIVE
            """)
    Optional<Workspace> findActiveByIdForUpdate(UUID workspaceId);
}
