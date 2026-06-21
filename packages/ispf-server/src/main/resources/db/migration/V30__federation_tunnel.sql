CREATE TABLE IF NOT EXISTS federation_inbound_registrations (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(128) NOT NULL,
    registration_code_hash  VARCHAR(128) NOT NULL,
    path_prefix             VARCHAR(256) NOT NULL DEFAULT 'root.platform',
    expires_at              TIMESTAMP NOT NULL,
    consumed_at             TIMESTAMP,
    created_by              VARCHAR(128),
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_inbound_reg_name
    ON federation_inbound_registrations (name);

CREATE TABLE IF NOT EXISTS federation_outbound_agents (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    hub_base_url        VARCHAR(512) NOT NULL,
    registration_code_enc VARCHAR(1024),
    session_token_enc   VARCHAR(1024),
    path_prefix         VARCHAR(256) NOT NULL DEFAULT 'root.platform',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    tunnel_status       VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    linked_peer_id      UUID,
    last_error          VARCHAR(1024),
    last_connected_at   TIMESTAMP,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_outbound_agent_name
    ON federation_outbound_agents (name);

CREATE TABLE IF NOT EXISTS federation_tunnel_sessions (
    session_id          VARCHAR(64) PRIMARY KEY,
    registration_id     UUID,
    peer_id             UUID NOT NULL,
    connected_at        TIMESTAMP NOT NULL,
    last_ping_at        TIMESTAMP NOT NULL,
    agent_version       VARCHAR(64),
    disconnected_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_federation_tunnel_sessions_peer
    ON federation_tunnel_sessions (peer_id);
