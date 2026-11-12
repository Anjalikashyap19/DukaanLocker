-- Details captured for FSSAI annual-return filing payments.
ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS filing_details TEXT;
