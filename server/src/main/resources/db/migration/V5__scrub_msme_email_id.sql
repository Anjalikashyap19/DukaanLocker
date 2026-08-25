-- V5: Scrub MSME (Udyam) numbers previously stored in email_id.
-- Security: the Udyam number must not persist in email_id because it is
-- returned in API responses (AuthResponse.emailId), leaking the MSME number.
-- Replace such values with an opaque, unique dummy. Real emails already stored
-- on MSME users are left untouched. Idempotent: a second run matches nothing.

UPDATE users
SET email_id = 'msme-user-' || id || '@dukaanlocker.com'
WHERE msme_user = true
  AND email_id IS NOT NULL
  AND email_id ~* '^udyam-[a-z]{2}-[0-9]{2}-[0-9]{7}$';
