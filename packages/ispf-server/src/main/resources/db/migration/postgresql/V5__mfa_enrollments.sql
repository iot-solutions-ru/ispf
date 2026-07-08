-- BL-153: persisted TOTP MFA enrollments

CREATE TABLE IF NOT EXISTS mfa_enrollments (
    username    VARCHAR(64)  PRIMARY KEY,
    secret      VARCHAR(128) NOT NULL,
    enrolled_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mfa_enrollments_enrolled ON mfa_enrollments(enrolled_at);
