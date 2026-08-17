-- Marks users created via MSME (Udyam) registration so they can be excluded
-- from the email+password (and biometric) login flows. MSME users must instead
-- authenticate with their Udyam number + OTP.
--
-- Added as nullable first with a default, backfill existing MSME users by
-- joining shops -> documents where document_type = 'MSME_CERTIFICATE', then
-- enforce NOT NULL.
ALTER TABLE users ADD COLUMN msme_user boolean DEFAULT FALSE;

UPDATE users u
SET msme_user = TRUE
WHERE u.id IN (
    SELECT s.user_id
    FROM shops s
    JOIN documents d ON d.shop_id = s.id
    WHERE d.document_type = 'MSME_CERTIFICATE'
);

ALTER TABLE users ALTER COLUMN msme_user SET NOT NULL;
