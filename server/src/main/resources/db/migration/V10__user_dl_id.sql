-- DL ID (Dukaan Locker ID): 4-digit folder identifier derived as userId + 1110.
-- Used for storage paths (documents/1111_45/...) and renewal-ops fulfillment.
-- The entity has had this column since before Flyway baseline; add defensively
-- and backfill from the deterministic formula.

ALTER TABLE users ADD COLUMN IF NOT EXISTS dl_id VARCHAR(10);

UPDATE users
SET dl_id = LPAD((id + 1110)::text, 4, '0')
WHERE dl_id IS NULL OR dl_id = '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema() AND indexname = 'uq_users_dl_id'
    ) THEN
        CREATE UNIQUE INDEX uq_users_dl_id ON users(dl_id);
    END IF;
END $$;
