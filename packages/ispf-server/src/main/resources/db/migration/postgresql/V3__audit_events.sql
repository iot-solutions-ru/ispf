-- Immutable security audit trail — BL-156

CREATE TABLE IF NOT EXISTS audit_events (
    id           VARCHAR(64)  PRIMARY KEY,
    category     VARCHAR(64)  NOT NULL,
    action       VARCHAR(128) NOT NULL,
    actor        VARCHAR(128),
    target_type  VARCHAR(64),
    target_id    VARCHAR(512),
    details_json TEXT,
    occurred_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_events_occurred ON audit_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_category ON audit_events(category, occurred_at DESC);
