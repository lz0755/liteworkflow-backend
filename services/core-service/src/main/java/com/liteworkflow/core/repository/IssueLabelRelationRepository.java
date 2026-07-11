package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueLabelRelation;
import com.liteworkflow.core.domain.IssueLabelRelationId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IssueLabelRelationRepository extends JpaRepository<IssueLabelRelation, IssueLabelRelationId> {

    List<IssueLabelRelation> findByIdIssueIdIn(Collection<UUID> issueIds);

    List<IssueLabelRelation> findByIdIssueId(UUID issueId);

    @Modifying
    @Query("delete from IssueLabelRelation r where r.id.issueId = :issueId")
    void deleteByIssueId(UUID issueId);

}
