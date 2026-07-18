-- ADR-0049 Wave 1: step-level workflow execution journal
-- Wave 3: dead letters for exhausted retries

CREATE TABLE IF NOT EXISTS workflow_execution_steps (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    token_id        VARCHAR(64),
    seq             INTEGER      NOT NULL,
    node_id         VARCHAR(128) NOT NULL,
    node_type       VARCHAR(64)  NOT NULL,
    started_at      TIMESTAMP    NOT NULL,
    ended_at        TIMESTAMP,
    status          VARCHAR(16)  NOT NULL,
    attempt         INTEGER      NOT NULL DEFAULT 1,
    input_json      TEXT,
    output_json     TEXT,
    error_json      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_execution_steps_instance
    ON workflow_execution_steps(instance_id, seq);
CREATE INDEX IF NOT EXISTS idx_workflow_execution_steps_path
    ON workflow_execution_steps(workflow_path, started_at DESC);

CREATE TABLE IF NOT EXISTS workflow_dead_letters (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    payload_json    TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_dead_letters_path
    ON workflow_dead_letters(workflow_path, created_at DESC);

-- ADR-0049 Wave 4: encrypted credentials vault
CREATE TABLE IF NOT EXISTS platform_credentials (
    id              VARCHAR(64)  PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL UNIQUE,
    kind            VARCHAR(64)  NOT NULL,
    cipher_text     TEXT         NOT NULL,
    metadata_json   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_credentials_kind
    ON platform_credentials(kind);
