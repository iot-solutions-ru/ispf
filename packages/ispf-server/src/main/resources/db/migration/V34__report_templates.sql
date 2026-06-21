CREATE TABLE report_templates (
    report_path VARCHAR(512) PRIMARY KEY,
    format      VARCHAR(16)  NOT NULL,
    content     BINARY VARYING NOT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
