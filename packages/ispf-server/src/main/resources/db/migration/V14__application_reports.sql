CREATE TABLE application_reports (
    id UUID PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    report_id VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    query_sql TEXT NOT NULL,
    parameters_json TEXT,
    columns_json TEXT NOT NULL,
    max_rows INTEGER NOT NULL DEFAULT 1000,
    deployed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_report UNIQUE (app_id, report_id)
);
