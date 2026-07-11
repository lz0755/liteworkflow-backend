package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.ConsumedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {
}
