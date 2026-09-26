CREATE TABLE IF NOT EXISTS messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID            NOT NULL,
    role                VARCHAR(16)     NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content             TEXT            NOT NULL,
    rewritten_query     TEXT,           -- the standalone retrieval query (ASSISTANT messages)
    latency_ms          BIGINT,
    provider            VARCHAR(32),    -- 'gemini', 'groq', 'none'
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at      ON messages(created_at);
