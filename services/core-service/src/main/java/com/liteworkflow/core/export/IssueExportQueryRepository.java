package com.liteworkflow.core.export;

import com.liteworkflow.core.domain.Issue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface IssueExportQueryRepository extends Repository<Issue, UUID> {

    @Query("""
            select max(i.issueNumber) from Issue i
             where i.projectId = :projectId
               and i.deletedAt is null
            """)
    Long findHighWatermark(UUID projectId);

    @Query("""
            select i from Issue i
             where i.projectId = :projectId
               and i.deletedAt is null
               and i.issueNumber > :afterIssueNumber
               and i.issueNumber <= :highWatermark
             order by i.issueNumber asc
            """)
    List<Issue> findBatch(
            UUID projectId,
            long afterIssueNumber,
            long highWatermark,
            Pageable pageable);
}
