-- Stores single-use OTP challenges for the MSME (Udyam) number + OTP login
-- flow. The plaintext OTP is never persisted — only its BCrypt hash. One
-- active challenge per (mobile, purpose) is maintained at a time.
CREATE TABLE otp_challenges (
    id          BIGSERIAL PRIMARY KEY,
    msme_number VARCHAR(255) NOT NULL,
    mobile      VARCHAR(20)  NOT NULL,
    otp_hash    VARCHAR(255) NOT NULL,
    purpose     VARCHAR(50)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_otp_mobile_purpose ON otp_challenges (mobile, purpose);
