package com.liteworkflow.infra.export;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportConsumedEventRepository extends JpaRepository<ExportConsumedEvent, UUID> {
}
