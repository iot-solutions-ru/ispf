CREATE TABLE agent_session_documents (
    doc_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    description VARCHAR(512),
    content_text TEXT NOT NULL,
    byte_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_agent_session_documents_session
        FOREIGN KEY (session_id) REFERENCES agent_sessions(session_id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_session_documents_session ON agent_session_documents (session_id);
