package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueAssignee;
import com.liteworkflow.core.domain.IssueAssigneeId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IssueAssigneeRepository extends JpaRepository<IssueAssignee, IssueAssigneeId> {

    List<IssueAssignee> findByIdIssueIdIn(Collection<UUID> issueIds);

    List<IssueAssignee> findByIdIssueId(UUID issueId);

    @Modifying
    @Query("delete from IssueAssignee a where a.id.issueId = :issueId")
    void deleteByIssueId(UUID issueId);
}
