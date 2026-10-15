-- ADR-0063: per-application Git Catalog binding (team remote + sync metadata).
CREATE TABLE IF NOT EXISTS application_git_bindings (
    app_id VARCHAR(64) PRIMARY KEY REFERENCES applications(app_id),
    provider VARCHAR(32) NOT NULL,
    remote_url VARCHAR(1024) NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    bundle_path VARCHAR(512) NOT NULL DEFAULT 'bundle.json',
    credential_object_path VARCHAR(512),
    last_synced_sha VARCHAR(64),
    last_synced_version VARCHAR(64),
    last_synced_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
