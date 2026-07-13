ALTER TABLE infra.stored_files
    ADD COLUMN document_id UUID,
    ADD COLUMN source_version BIGINT;

UPDATE infra.stored_files
SET document_id = id, source_version = 1
WHERE document_id IS NULL;

ALTER TABLE infra.stored_files
    ALTER COLUMN document_id SET NOT NULL,
    ALTER COLUMN source_version SET NOT NULL,
    ADD CONSTRAINT ck_stored_files_source_version CHECK (source_version >= 1),
    ADD CONSTRAINT uq_stored_files_document_version UNIQUE (document_id, source_version);

CREATE UNIQUE INDEX uq_stored_files_active_document
    ON infra.stored_files (document_id)
    WHERE status = 'ACTIVE';
