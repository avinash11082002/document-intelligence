CREATE TABLE IF NOT EXISTS documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        VARCHAR(128)    NOT NULL,
    filename        VARCHAR(512)    NOT NULL,
    content_type    VARCHAR(128)    NOT NULL,
    file_size_bytes BIGINT          NOT NULL,
    content_hash    VARCHAR(64)     NOT NULL,
    file_path       VARCHAR(1024)   NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'UPLOADED',
    document_type   VARCHAR(128),
    extracted_fields JSONB,
    chunk_count     INTEGER,
    failure_reason  TEXT,
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,

    CONSTRAINT uq_content_hash_owner UNIQUE (content_hash, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_documents_owner_id     ON documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_documents_status       ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_content_hash ON documents(content_hash);
CREATE INDEX IF NOT EXISTS idx_documents_created_at   ON documents(created_at DESC);
