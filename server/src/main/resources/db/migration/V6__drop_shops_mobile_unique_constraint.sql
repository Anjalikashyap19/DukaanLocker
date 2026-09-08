-- Drop the unique constraint on shops.mobile so multiple shops can share
-- the same mobile number (e.g. a user with multiple branches using one contact).
ALTER TABLE shops DROP CONSTRAINT IF EXISTS shops_mobile_key;
