package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.Project;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("""
            select p from Project p
             where p.id = :projectId
               and p.status = com.liteworkflow.core.domain.ProjectStatus.ACTIVE
            """)
    Optional<Project> findActiveById(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Project p
             where p.id = :projectId
               and p.status = com.liteworkflow.core.domain.ProjectStatus.ACTIVE
            """)
    Optional<Project> findActiveByIdForUpdate(UUID projectId);

    @Query("""
            select p from Project p
             where p.workspaceId = :workspaceId
               and p.status = com.liteworkflow.core.domain.ProjectStatus.ACTIVE
               and (:workspaceManager = true or exists (
                   select m.id from ProjectMember m
                    where m.projectId = p.id
                      and m.userId = :userId
                      and m.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               ))
             order by lower(p.name), p.id
            """)
    Page<Project> findAccessible(
            UUID workspaceId, UUID userId, boolean workspaceManager, Pageable pageable);

    @Query("""
            select p.id from Project p
             where p.workspaceId = :workspaceId
               and p.status = com.liteworkflow.core.domain.ProjectStatus.ACTIVE
            """)
    List<UUID> findActiveIdsByWorkspaceId(UUID workspaceId);
}
