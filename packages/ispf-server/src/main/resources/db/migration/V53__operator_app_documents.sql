CREATE TABLE operator_app_documents (
    doc_id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(128) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    description VARCHAR(512),
    content_text TEXT NOT NULL,
    byte_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_operator_app_documents_app ON operator_app_documents (app_id);
