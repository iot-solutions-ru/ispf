CREATE TABLE application_sql_bindings (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    object_path VARCHAR(512) NOT NULL,
    variable_name VARCHAR(128) NOT NULL,
    query_sql TEXT NOT NULL,
    refresh_mode VARCHAR(32) NOT NULL DEFAULT 'on_schedule',
    refresh_interval_ms BIGINT,
    value_field VARCHAR(128) DEFAULT 'value',
    trigger_object_path VARCHAR(512),
    trigger_function_name VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_refreshed_at TIMESTAMP,
    deployed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_sql_binding UNIQUE (app_id, object_path, variable_name)
);
