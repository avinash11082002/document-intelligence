-- Rename INDEXED → READY in all existing document records.
-- The DocumentStatus enum has been updated accordingly in the application code.
UPDATE documents SET status = 'READY' WHERE status = 'INDEXED';
