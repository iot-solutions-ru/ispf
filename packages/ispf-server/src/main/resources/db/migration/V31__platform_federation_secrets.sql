CREATE TABLE IF NOT EXISTS platform_federation_secrets (
    id           CHAR(1) NOT NULL PRIMARY KEY,
    secrets_key  VARCHAR(512) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT platform_federation_secrets_singleton CHECK (id = 'X')
);
