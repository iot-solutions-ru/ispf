CREATE TABLE alarm_shelves (
    id VARCHAR(64) PRIMARY KEY,
    object_path VARCHAR(512) NOT NULL,
    event_name VARCHAR(128) NOT NULL,
    alert_rule_path VARCHAR(512),
    shelved_by VARCHAR(128) NOT NULL,
    shelved_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    comment VARCHAR(1024),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_alarm_shelves_active_lookup ON alarm_shelves (object_path, event_name, active);
