CREATE TABLE platform_roles (
    name VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO platform_roles (name, display_name, description, built_in)
VALUES
    ('admin', 'admin', 'Full platform administration', TRUE),
    ('operator', 'operator', 'Operator HMI and read-only automation', TRUE);
