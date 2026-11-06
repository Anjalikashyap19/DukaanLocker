-- V21: Add duration_years column to renewal_orders (nullable for backward compatibility)
ALTER TABLE renewal_orders ADD COLUMN IF NOT EXISTS duration_years INTEGER;
