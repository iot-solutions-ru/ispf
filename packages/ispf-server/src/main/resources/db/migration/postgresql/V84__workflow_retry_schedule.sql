-- ADR-0049 Wave 3: durable async workflow retry schedule

CREATE TABLE IF NOT EXISTS workflow_retry_schedule (
    id                  VARCHAR(64)  PRIMARY KEY,
    workflow_path       VARCHAR(512) NOT NULL,
    source_instance_id  VARCHAR(64)  NOT NULL,
    attempt             INTEGER      NOT NULL,
    due_at              TIMESTAMP    NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    input_json          TEXT,
    last_error          TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at          TIMESTAMP,
    completed_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_retry_due
    ON workflow_retry_schedule(status, due_at);
CREATE INDEX IF NOT EXISTS idx_workflow_retry_path
    ON workflow_retry_schedule(workflow_path, created_at DESC);
