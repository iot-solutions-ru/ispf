ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS connection_mode VARCHAR(32) NOT NULL DEFAULT 'HTTP_PULL';

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS auth_mode VARCHAR(32) NOT NULL DEFAULT 'STATIC_TOKEN';

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMP;

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS auth_username VARCHAR(128);

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS auth_secret_enc VARCHAR(1024);

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS auth_status VARCHAR(32) NOT NULL DEFAULT 'OK';

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS last_auth_at TIMESTAMP;

ALTER TABLE federation_peers
    ADD COLUMN IF NOT EXISTS last_auth_error VARCHAR(1024);
