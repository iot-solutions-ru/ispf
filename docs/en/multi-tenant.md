> **Language:** Canonical English. Russian edition: [ru/multi-tenant.md](../ru/multi-tenant.md).

# Multi-tenant SaaS (BL-125, BL-126, BL-155)

Logical separation of object trees by tenant in a single ISPF instance, with a **tenant-admin** local owner per customer branch.

## Actors

| Actor | Role | `tenant_id` | Capabilities |
|-------|------|-------------|--------------|
| Platform ops | `admin` | **NULL** (required) | Create/delete tenants, global security, sees all. Only global admin bypasses `TenantScopeService`. |
| Tenant local admin | `tenant-admin` | required | Full owner of `root.tenant.{id}.*`: CRUD under branch, create users/roles for that tenant, assign roles within tenant, manage own quotas/usage. **Cannot** create tenants, see other tenants, access `root.platform.*`, or grant global `admin`. |
| Tenant user | `operator` / `developer` / custom | required | Scoped to tenant branch + ACL |

**Critical rule:** `admin` role with non-null `tenant_id` is **forbidden**. Global admin always has `tenant_id` NULL. `tenant-admin` never bypasses tenant scope.

## Path structure

```text
root
├── root.platform                    ← shared / default (legacy demo; global admin)
└── root.tenant
    └── root.tenant.acme
        └── root.tenant.acme.platform
            ├── .devices
            ├── .dashboards
            └── .security
                ├── .users
                └── .roles
```

**Principle:** object path is the stable namespace identifier; tenant is not tied to HTTP host.

## Create tenant + local admin

```http
POST /api/v1/tenants
Authorization: Bearer <global-admin-token>
Content-Type: application/json

{
  "tenantId": "acme",
  "displayName": "Acme Corp",
  "enabled": true,
  "adminUsername": "acme-admin",
  "adminPassword": "change-me",
  "adminDisplayName": "Acme Admin"
}
```

Defaults when omitted: `adminUsername` = `{tenantId}-admin`, password auto-generated. Response includes **one-time** `adminUsername` and `adminPassword` (not a hash). Bootstrap creates the platform tree, `security/users/roles`, the local admin user (`roles: ["tenant-admin"]`, `tenant_id` set), and OWNER ACL on `root.tenant.{id}` (plus EDITOR for built-in operator/developer so the branch stays usable).

## API

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/v1/tenants` | global `admin` | List tenants |
| POST | `/api/v1/tenants` | global `admin` | Create tenant + bootstrap + local admin |
| GET | `/api/v1/tenants/{tenantId}` | global `admin` or that tenant's `tenant-admin` | Get tenant |
| DELETE | `/api/v1/tenants/{tenantId}` | global `admin` | Delete tenant and subtree |
| PUT | `/api/v1/tenants/{tenantId}/users/{username}` | global `admin` | Assign tenant to user |
| PUT | `/api/v1/tenants/{tenantId}/quotas` | global `admin` or own `tenant-admin` | Quotas: `maxDevices`, `maxObjects` |
| GET | `/api/v1/tenants/{tenantId}/usage` | global `admin` or own `tenant-admin` | Current usage |
| GET/POST/PUT/DELETE | `/api/v1/security/users/**` | `admin` or `tenant-admin` | Users (tenant-admin: same `tenant_id` only; cannot assign `admin`) |
| GET/POST/PUT/DELETE | `/api/v1/security/roles/**` | `admin` or `tenant-admin` | Roles (tenant-admin: built-ins read-only + own `tenant_id` custom roles) |

Login response includes `tenantId` when assigned. Cluster, license, federation, and audit remain **global-admin only**.

## Scope enforcement

User with `tenant_id` and **without** global `admin`:

- **Storage** remains `root.tenant.{id}.platform.*`
- **Sole-tenant / white-label API surface:** request and response paths use `root` / `root.platform.*` as if that were the only world (`TenantVirtualRoot`). Navigation stubs `root.tenant` / `root.tenant.{id}` are hidden.
- **Write:** create/update/delete blocked with `403` outside the caller’s platform subtree (including other tenants’ canonical paths)
- **Role templates:** `root.platform.*` scope prefixes remap to `root.tenant.{id}.platform.*`
- History/events/WebSocket enforce the same expand-on-request / collapse-on-response rules

Global `admin` sees and writes everything with **canonical** paths (no virtual rewrite). `tenant-admin` has OWNER-level object access within their branch only.

## Local platform DB forbidden for tenants

Anyone with a non-null `tenant_id` (including **tenant-admin**) must **not** use the local/platform database. They may only use **external** JDBC data sources (remote DBs). Global `admin` is unchanged.

| Rule | Tenant callers |
|------|----------------|
| `connectionMode=internal` | Forbidden (create / update / test / execute) |
| External JDBC URL | Reject localhost, `127.0.0.0/8`, `::1`, link-local, and the host from `spring.datasource.url`. Driver allowlist unchanged. |
| Script / BFF SQL with blank `dataSourcePath` | Forbidden (falls through to platform catalog) |
| Migrations / SQL bindings / reports | `dataSourcePath` must be an allowed external DS |

Tenant external data sources live under the sole-tenant path `root.platform.data-sources` (storage: `root.tenant.{id}.platform.data-sources`). Enforcement is centralized in `TenantLocalDataAccessGuard`. Object-tree persistence via `ApplicationSchemaSession.callWithPlatformCatalog` (without arbitrary SQL) remains allowed.

## Quotas (BL-126)

| Quota | Enforcement |
|-------|-------------|
| `maxObjects` | Objects under `root.tenant.{id}.platform.*` |
| `maxDevices` | Objects of type `DEVICE` in the same subtree |

Exceeded → `409 Conflict` on `POST /api/v1/objects`.

## Web Console

- **Global admin:** node **`root.tenant`** → **Tenants** panel — create tenant with optional local-admin password; assign users. Paths remain canonical.
- **Tenant users:** sole-tenant tree — explorer speaks `root` / `root.platform.*` only (server virtual root). Existing console helpers that hardcode `root.platform.*` work without a client remapper.

## Isolation mode (BL-155)

| Property | Default | Description |
|----------|---------|-------------|
| `ispf.tenant.isolation-mode` | `logical` | `logical` — path namespaces + API scope; `hard` — also provision/drop per-tenant PostgreSQL schema |
| `ispf.tenant.schema-prefix` | `tenant_` | Schema prefix when `hard` (`tenant_acme` for tenant `acme`) |
| `ispf.tenant.oidc-tenant-claim` | `tenant_id` | JWT claim mapped by `TenantScopeService` (OIDC). Empty claim name disables mapping; local user→tenant assignment still applies. |
| `ispf.tenant.db-row-isolation` | `true` | PostgreSQL RLS session GUCs (`app.tenant_id` / `app.tenant_bypass`) on shared object tables. No-op on H2. |

Env: `ISPF_TENANT_ISOLATION_MODE=logical|hard`, `ISPF_TENANT_SCHEMA_PREFIX=tenant_`, `ISPF_TENANT_OIDC_CLAIM=tenant_id`, `ISPF_TENANT_DB_ROW_ISOLATION=true|false`.

### DB row isolation (PostgreSQL RLS)

When `ispf.tenant.db-row-isolation=true` (default) on PostgreSQL:

- Migration `V86__tenant_row_level_security.sql` enables **FORCE ROW LEVEL SECURITY** on path-scoped tables (`object_nodes`, `object_variables`, `object_acl_entries`, `event_history`, `variable_samples`, `object_config_audit`, `object_edit_leases`, `alarm_shelves`, `alarm_shelf_requests`).
- Per request: global admin / unauthenticated → `app.tenant_bypass=on`; tenant user → `bypass=off` + `app.tenant_id=<id>`.
- Unset GUCs default-allow (Flyway / bootstrap / ObjectTreeLoad).
- H2 tests do **not** enforce RLS (Flyway skips V86 on H2).

### Honesty / status

| Layer | Status |
|-------|--------|
| Logical SaaS A≠B (path + API + role scope + tenant-admin) | **Done** |
| Sole-tenant / white-label virtual root (`root.platform.*` API surface) | **Done** (`TenantVirtualRoot`) |
| OIDC `tenant_id` claim mapping | **Done** |
| Hard mode schema provision/drop | **Done** (hooks) |
| DB row A≠B on shared platform object tables | **RLS Done** when `ispf.tenant.db-row-isolation=true` (PostgreSQL); physical schema split still optional |

### Comparison

| | Logical | Hard |
|---|---------|------|
| DB schema | Shared + RLS | Per-tenant schema provisioned; platform tables still shared + RLS |
| Path scope | `root.tenant.{id}.*` | Same + schema provision/drop |
| OIDC tenant claim | `tenant_id` (configurable) | Same |
| Local admin | `tenant-admin` bootstrap | Same |

See also [federation](federation.md), [roadmap](roadmap.md), [security](security.md).
