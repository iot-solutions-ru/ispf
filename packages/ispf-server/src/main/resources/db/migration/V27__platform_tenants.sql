CREATE TABLE IF NOT EXISTS platform_tenants (
    tenant_id    VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_platform_users_tenant ON platform_users (tenant_id);
