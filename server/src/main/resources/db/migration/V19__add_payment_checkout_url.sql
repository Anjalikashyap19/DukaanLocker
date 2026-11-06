-- Store Easebuzz EasyCollect payment URL returned by the create-link API
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS gateway_checkout_url VARCHAR(500);
