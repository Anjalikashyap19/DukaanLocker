-- Distinguish what a payment is for. Existing rows are all document renewals.
ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(40) NOT NULL DEFAULT 'RENEWAL';

CREATE INDEX IF NOT EXISTS idx_payment_transactions_purpose
    ON payment_transactions(purpose);
