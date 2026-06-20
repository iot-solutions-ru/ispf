CREATE TABLE IF NOT EXISTS federation_peers (
    id           UUID PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    base_url     VARCHAR(512) NOT NULL,
    auth_token   VARCHAR(512),
    path_prefix  VARCHAR(256) NOT NULL DEFAULT '',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    description  VARCHAR(1024),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_peer_name ON federation_peers (name);
