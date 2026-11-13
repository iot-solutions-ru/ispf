-- Ed25519 public keys used to verify function signatures. Private keys stay with the signer.

CREATE TABLE IF NOT EXISTS user_signature_keys (
    username    VARCHAR(128) PRIMARY KEY,
    public_key  VARCHAR(256) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
