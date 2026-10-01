-- SHA-256 of the stored file bytes, so one physical document cannot be filed
-- under two different shops of the same account. Without it, uploading the same
-- trade licence to a second (accidentally duplicated) shop was accepted, because
-- the only uniqueness on documents is (shop_id, document_type) and the app never
-- sends document_number for a self-upload.
--
-- Nullable and backfilled nowhere: rows written before this migration simply
-- have no hash, so they are invisible to the check. Deliberately not unique -
-- two accounts may legitimately hold byte-identical paperwork.

ALTER TABLE documents ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- Non-unique index only: the lookup is "is this hash present on another shop of
-- this owner?", never a uniqueness constraint (existing dirty data must load).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema() AND indexname = 'idx_documents_content_hash'
    ) THEN
        CREATE INDEX idx_documents_content_hash ON documents(content_hash);
    END IF;
END $$;
