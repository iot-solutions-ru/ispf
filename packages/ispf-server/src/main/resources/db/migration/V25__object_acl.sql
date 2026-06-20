CREATE TABLE IF NOT EXISTS object_acl_entries (
    id              UUID PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL,
    principal_type  VARCHAR(16)  NOT NULL,
    principal_id    VARCHAR(128) NOT NULL,
    permission      VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_object_acl_path ON object_acl_entries (object_path);
CREATE UNIQUE INDEX IF NOT EXISTS uq_object_acl_grant
    ON object_acl_entries (object_path, principal_type, principal_id, permission);
