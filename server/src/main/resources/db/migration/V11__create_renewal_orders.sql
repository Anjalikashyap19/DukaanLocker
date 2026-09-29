-- Renewal orders: user requests renewal of an expiring/expired document,
-- ops fulfills it and uploads the certificate into the user's DL-ID locker folder.

CREATE TABLE IF NOT EXISTS renewal_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    shop_id BIGINT NOT NULL REFERENCES shops(id),
    document_type VARCHAR(50) NOT NULL,
    document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    notes TEXT,
    dl_id VARCHAR(10),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_renewal_orders_user ON renewal_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_renewal_orders_status ON renewal_orders(status);
