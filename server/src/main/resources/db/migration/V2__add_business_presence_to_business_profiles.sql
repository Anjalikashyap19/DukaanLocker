-- Adds the business_presence column to business_profiles. The column was
-- declared on the entity (@Column(nullable = false)) but never applied to the
-- database because prod uses ddl-auto=validate, so Hibernate would not create
-- it and validation failed at startup with:
--   Schema-validation: missing column [business_presence] in table [business_profiles]
--
-- Add as nullable first, backfill existing rows, then enforce NOT NULL
-- (the existing single row cannot receive a value during a single ALTER ... ADD COLUMN NOT NULL).
ALTER TABLE business_profiles ADD COLUMN business_presence varchar(255);
UPDATE business_profiles SET business_presence = 'SINGLE_PHYSICAL' WHERE business_presence IS NULL;
ALTER TABLE business_profiles ALTER COLUMN business_presence SET NOT NULL;
