package com.liteworkflow.infra.export;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportFileRepository extends JpaRepository<ExportFile, UUID> {
    Optional<ExportFile> findByExportJobId(UUID exportJobId);
}
