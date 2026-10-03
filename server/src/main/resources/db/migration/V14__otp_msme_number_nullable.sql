-- Password-reset OTP challenges are keyed by mobile alone, so they carry no
-- Udyam (MSME) number. Existing MSME_LOGIN rows keep their value.
ALTER TABLE otp_challenges ALTER COLUMN msme_number DROP NOT NULL;
