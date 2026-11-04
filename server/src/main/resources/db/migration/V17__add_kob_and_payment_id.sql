-- Store FSSAI kind-of-business on the document for State-tier pricing at renewal time
ALTER TABLE documents ADD COLUMN IF NOT EXISTS kind_of_business VARCHAR(100);

-- Link renewal orders to the payment transaction that fulfilled them
ALTER TABLE renewal_orders ADD COLUMN IF NOT EXISTS payment_id VARCHAR(100);
ALTER TABLE renewal_orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20);
