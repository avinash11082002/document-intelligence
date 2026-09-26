-- Add role to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';

-- Add summary, page_count, suggested_questions to documents
ALTER TABLE documents ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS page_count INTEGER;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS suggested_questions JSONB;

-- Index for querying by role (useful for admin queries)
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
