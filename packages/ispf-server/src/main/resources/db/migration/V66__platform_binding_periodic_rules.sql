CREATE TABLE platform_binding_periodic_rules (
    object_path   VARCHAR(512) NOT NULL,
    rule_id       VARCHAR(128) NOT NULL,
    periodic_ms   BIGINT       NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at   TIMESTAMP,
    next_run_at   TIMESTAMP  NOT NULL,
    PRIMARY KEY (object_path, rule_id)
);

CREATE INDEX idx_binding_periodic_next_run
    ON platform_binding_periodic_rules (next_run_at);
