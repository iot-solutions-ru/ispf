-- BL-158: persisted alarm shelving approval requests

CREATE TABLE IF NOT EXISTS alarm_shelf_requests (
    id               VARCHAR(64)  PRIMARY KEY,
    object_path      VARCHAR(512) NOT NULL,
    event_name       VARCHAR(128) NOT NULL,
    alert_rule_path  VARCHAR(512),
    duration_minutes INTEGER,
    comment          VARCHAR(1024),
    requested_by     VARCHAR(128) NOT NULL,
    requested_at     TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alarm_shelf_requests_requested_at
    ON alarm_shelf_requests (requested_at DESC);
