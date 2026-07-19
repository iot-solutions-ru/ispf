-- BL-155: SaaS tenant-scoped custom roles (tenant-admin local owners)

ALTER TABLE platform_roles ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_platform_roles_tenant ON platform_roles (tenant_id);
