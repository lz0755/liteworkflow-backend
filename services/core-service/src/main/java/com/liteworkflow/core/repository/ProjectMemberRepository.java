package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.ProjectMember;
import com.liteworkflow.core.domain.ProjectRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMember m where m.projectId = :projectId and m.userId = :userId")
    Optional<ProjectMember> findByProjectIdAndUserIdForUpdate(UUID projectId, UUID userId);

    @Query("""
            select m.role from ProjectMember m, Project p
             where m.projectId = p.id
               and m.projectId = :projectId
               and m.userId = :userId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and p.status = com.liteworkflow.core.domain.ProjectStatus.ACTIVE
            """)
    Optional<ProjectRole> findActiveRole(UUID projectId, UUID userId);

    Page<ProjectMember> findByProjectIdAndStatusOrderByJoinedAtAscIdAsc(
            UUID projectId, MemberStatus status, Pageable pageable);

    boolean existsByProjectIdAndUserIdAndStatus(UUID projectId, UUID userId, MemberStatus status);

    long countByProjectIdAndStatusAndRole(UUID projectId, MemberStatus status, ProjectRole role);

    @Query("""
            select m.userId from ProjectMember m
             where m.projectId = :projectId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
            """)
    List<UUID> findActiveUserIdsByProjectId(UUID projectId);

    @Query("""
            select m.userId from ProjectMember m
             where m.projectId = :projectId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and m.userId in :userIds
            """)
    List<UUID> findActiveUserIdsByProjectIdAndUserIdIn(UUID projectId, List<UUID> userIds);

    @Query("""
            select m.userId from ProjectMember m, Project p, WorkspaceMember wm, UserDirectory u
             where m.projectId = :projectId
               and p.id = m.projectId
               and wm.workspaceId = p.workspaceId
               and wm.userId = m.userId
               and u.userId = m.userId
               and m.userId in :userIds
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and wm.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and u.accountStatus = com.liteworkflow.core.domain.AccountStatus.ACTIVE
            """)
    List<UUID> findEligibleAssigneeIds(UUID projectId, List<UUID> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from ProjectMember m, Project p
             where m.projectId = p.id
               and p.workspaceId = :workspaceId
               and m.userId = :userId
               and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
             order by m.projectId, m.id
            """)
    List<ProjectMember> findActiveByWorkspaceIdAndUserIdForUpdate(UUID workspaceId, UUID userId);
}
