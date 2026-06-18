CREATE TABLE IF NOT EXISTS alert_rules (
    id                 VARCHAR(64)  PRIMARY KEY,
    name               VARCHAR(256) NOT NULL,
    object_path       VARCHAR(512) NOT NULL,
    watch_variable     VARCHAR(128) NOT NULL,
    condition_expr     TEXT         NOT NULL,
    event_name         VARCHAR(128) NOT NULL,
    payload_variable   VARCHAR(128),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    edge_trigger       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_condition_met BOOLEAN,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_object ON alert_rules(object_path, watch_variable);
