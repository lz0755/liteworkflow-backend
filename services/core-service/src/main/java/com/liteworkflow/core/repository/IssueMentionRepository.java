package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueMention;
import com.liteworkflow.core.domain.IssueMentionId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueMentionRepository extends JpaRepository<IssueMention, IssueMentionId> {

    List<IssueMention> findByCommentId(UUID commentId);

    List<IssueMention> findByCommentIdIn(Collection<UUID> commentIds);

    void deleteByCommentIdAndUserIdIn(UUID commentId, Collection<UUID> userIds);

    void deleteByCommentId(UUID commentId);
}
