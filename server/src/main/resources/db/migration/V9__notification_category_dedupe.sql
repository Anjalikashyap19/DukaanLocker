-- Notification taxonomy (category), scheduler idempotency (dedupe_key),
-- and schema-drift fixes for columns the entity gained after V8.

-- 1. Schema drift: entity has these, V8 never created them
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_in_bin BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Category taxonomy: MISSING_DOC / ALERT / ACTIVITY
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS category VARCHAR(30);

UPDATE notifications
SET category = CASE
    WHEN type = 'MISSING_DOCUMENT' THEN 'MISSING_DOC'
    WHEN type IN ('EXPIRING_SOON', 'EXPIRED') THEN 'ALERT'
    ELSE 'ACTIVITY'
END
WHERE category IS NULL;

-- 3. Idempotency key for scheduler dedup; NULL on legacy rows (unique per user)
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
    ON notifications(user_id, dedupe_key);

-- 4. Supports the scheduler dedup/quota predicates
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_ref_created
    ON notifications(user_id, type, reference_id, created_at);

-- 5. created_at must be timestamptz to match the Instant mapping
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'notifications'
          AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE notifications ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;
    END IF;
END $$;
