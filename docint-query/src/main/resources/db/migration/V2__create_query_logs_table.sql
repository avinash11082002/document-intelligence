CREATE TABLE IF NOT EXISTS query_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        VARCHAR(128)    NOT NULL,
    document_id     UUID,
    question        TEXT            NOT NULL,
    question_hash   VARCHAR(64)     NOT NULL,
    answer          TEXT,
    source_chunks   JSONB,
    cache_hit       BOOLEAN         NOT NULL DEFAULT FALSE,
    latency_ms      INTEGER,
    llm_provider    VARCHAR(64),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_query_logs_owner    ON query_logs(owner_id);
CREATE INDEX IF NOT EXISTS idx_query_logs_document ON query_logs(document_id);
