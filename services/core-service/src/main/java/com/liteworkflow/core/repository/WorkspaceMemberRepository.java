package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.WorkspaceMember;
import com.liteworkflow.core.domain.WorkspaceRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WorkspaceMember m where m.workspaceId = :workspaceId and m.userId = :userId")
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdForUpdate(UUID workspaceId, UUID userId);

    @Query("""
            select m.role from WorkspaceMember m, Workspace w
             where m.workspaceId = w.id
               and m.workspaceId = :workspaceId
               and m.userId = :userId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and w.status = com.liteworkflow.core.domain.WorkspaceStatus.ACTIVE
            """)
    Optional<WorkspaceRole> findActiveRole(UUID workspaceId, UUID userId);

    Page<WorkspaceMember> findByWorkspaceIdAndStatusOrderByJoinedAtAscIdAsc(
            UUID workspaceId, MemberStatus status, Pageable pageable);

    boolean existsByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, MemberStatus status);

    long countByWorkspaceIdAndStatusAndRole(
            UUID workspaceId, MemberStatus status, WorkspaceRole role);

    @Query("""
            select m.workspaceId from WorkspaceMember m
             where m.userId = :userId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
            """)
    List<UUID> findActiveWorkspaceIdsByUserId(UUID userId);

    @Query("""
            select m.userId from WorkspaceMember m
             where m.workspaceId = :workspaceId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
            """)
    List<UUID> findActiveUserIdsByWorkspaceId(UUID workspaceId);

    @Query("""
            select m.userId from WorkspaceMember m
             where m.workspaceId = :workspaceId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and m.userId in :userIds
            """)
    List<UUID> findActiveUserIdsByWorkspaceIdAndUserIdIn(UUID workspaceId, List<UUID> userIds);
}
