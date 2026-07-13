package com.liteworkflow.infra.file;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndStatus(UUID id, FileStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFile f where f.id = :id and f.status = :status")
    Optional<StoredFile> findByIdAndStatusForUpdate(UUID id, FileStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFile f where f.documentId = :documentId and f.status = 'ACTIVE'")
    Optional<StoredFile> findActiveByDocumentIdForUpdate(UUID documentId);
    List<StoredFile> findByPurposeAndScopeIdAndStatusOrderByCreatedAtDesc(
            FilePurpose purpose, UUID scopeId, FileStatus status);
    List<StoredFile> findByStatusOrderByDeletedAtAsc(FileStatus status, Pageable pageable);
    List<StoredFile> findByStatusInOrderByDeletedAtAsc(Collection<FileStatus> statuses, Pageable pageable);
}
