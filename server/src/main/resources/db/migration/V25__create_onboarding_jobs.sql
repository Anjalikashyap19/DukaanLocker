-- Auto-onboarding queue fed by the FSSAI certificate feed
-- (https://iadv.shop/count_new_files.php). One row per certificate file; the
-- UNIQUE(file_name) constraint is the idempotency guard, because the feed has
-- no cursor and re-lists files until they are removed upstream.
CREATE TABLE IF NOT EXISTS onboarding_jobs (
    id             BIGSERIAL PRIMARY KEY,
    file_name      VARCHAR(255) NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    detail         VARCHAR(500),
    license_number VARCHAR(32),
    user_id        BIGINT,
    shop_id        BIGINT,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_onboarding_jobs_file_name UNIQUE (file_name)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_jobs_status ON onboarding_jobs (status);
