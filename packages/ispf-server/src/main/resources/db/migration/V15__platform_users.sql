CREATE TABLE platform_users (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    roles_json VARCHAR(255) NOT NULL,
    object_path VARCHAR(512) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE platform_auth_sessions (
    token VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL REFERENCES platform_users(username) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_auth_sessions_username ON platform_auth_sessions(username);
CREATE INDEX idx_platform_auth_sessions_expires ON platform_auth_sessions(expires_at);
