package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueSubscriber;
import com.liteworkflow.core.domain.IssueSubscriberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IssueSubscriberRepository extends JpaRepository<IssueSubscriber, IssueSubscriberId> {

    @Query("select s.userId from IssueSubscriber s where s.issueId = :issueId")
    List<UUID> findUserIdsByIssueId(UUID issueId);

    @Query("""
            select s.userId from IssueSubscriber s, Issue i, Project p, WorkspaceMember wm, UserDirectory u
             where s.issueId = :issueId
               and i.id = s.issueId
               and i.deletedAt is null
               and p.id = i.projectId
               and wm.workspaceId = p.workspaceId
               and wm.userId = s.userId
               and wm.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE
               and u.userId = s.userId
               and u.accountStatus = com.liteworkflow.core.domain.AccountStatus.ACTIVE
               and (wm.role in (com.liteworkflow.core.domain.WorkspaceRole.OWNER,
                                com.liteworkflow.core.domain.WorkspaceRole.ADMIN)
                    or exists (select pm.id from ProjectMember pm
                                where pm.projectId = p.id
                                  and pm.userId = s.userId
                                  and pm.status = com.liteworkflow.core.domain.MemberStatus.ACTIVE))
            """)
    List<UUID> findActiveReadableUserIdsByIssueId(UUID issueId);
}
