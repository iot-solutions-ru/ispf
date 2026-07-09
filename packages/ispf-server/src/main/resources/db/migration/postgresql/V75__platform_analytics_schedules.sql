-- BL-203: analytics calculation engine periodic schedule index
CREATE TABLE platform_analytics_schedules (
    tag_path VARCHAR(512) PRIMARY KEY,
    helper VARCHAR(64) NOT NULL,
    source_paths_json TEXT NOT NULL,
    window_bucket VARCHAR(16) NOT NULL,
    periodic_ms BIGINT NOT NULL DEFAULT 60000,
    on_change_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP,
    last_tick_at TIMESTAMP,
    last_error TEXT
);

CREATE INDEX idx_analytics_schedules_next_run
    ON platform_analytics_schedules (next_run_at);
