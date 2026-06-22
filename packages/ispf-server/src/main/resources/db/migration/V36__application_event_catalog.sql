CREATE TABLE application_event_catalog (
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id) ON DELETE CASCADE,
    event_id VARCHAR(128) NOT NULL,
    roles_json TEXT NOT NULL DEFAULT '[]',
    payload_schema_json TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (app_id, event_id)
);

CREATE INDEX idx_application_event_catalog_app ON application_event_catalog (app_id);
