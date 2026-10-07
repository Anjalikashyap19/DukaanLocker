-- Primary shop flag: the first shop created for an owner who had none
-- (i.e. brand-new profiles). Existing accounts are deliberately NOT
-- backfilled -- they keep today's behaviour of showing every shop on Home.
ALTER TABLE shops ADD COLUMN IF NOT EXISTS is_primary boolean NOT NULL DEFAULT false;
