CREATE TABLE IF NOT EXISTS message_citations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID            NOT NULL,
    document_id     UUID            NOT NULL,
    filename        VARCHAR(512)    NOT NULL,
    chunk_id        VARCHAR(256)    NOT NULL,
    page_number     INTEGER,
    snippet         TEXT,
    score           DOUBLE PRECISION,

    CONSTRAINT fk_citations_message  FOREIGN KEY (message_id)  REFERENCES messages(id)  ON DELETE CASCADE,
    CONSTRAINT fk_citations_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_citations_message_id  ON message_citations(message_id);
CREATE INDEX IF NOT EXISTS idx_citations_document_id ON message_citations(document_id);
