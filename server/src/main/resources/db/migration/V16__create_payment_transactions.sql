-- Payment transactions for FSSAI document renewal (Easebuzz gateway)
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    shop_id BIGINT NOT NULL REFERENCES shops(id),
    document_type VARCHAR(50) NOT NULL,
    document_id BIGINT,
    duration_years INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
    gov_charge DECIMAL(10,2) NOT NULL DEFAULT 0,
    service_charge DECIMAL(10,2) NOT NULL DEFAULT 0,
    gst_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    penalty DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / COMPLETED / FAILED
    gateway VARCHAR(50) NOT NULL DEFAULT 'easbuzz',
    gateway_order_id VARCHAR(100),
    gateway_payment_id VARCHAR(100),
    gateway_signature VARCHAR(200),
    renewal_order_id BIGINT REFERENCES renewal_orders(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_user ON payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_shop ON payment_transactions(shop_id);
