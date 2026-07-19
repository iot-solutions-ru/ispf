> **Language:** Canonical English. Russian edition: [ru/multi-tenant.md](../ru/multi-tenant.md).

# Multi-tenant namespaces (BL-125, BL-126)

Logical separation of object trees by tenant in a single ISPF instance.

## Path structure

```text
root
├── root.platform          ← shared / default (legacy demo)
└── root.tenant
    └── root.tenant.acme
        └── root.tenant.acme.platform
            ├── .devices
            └── .dashboards
```

**Principle:** object path is the stable namespace identifier; tenant is not tied to HTTP host.

## API (admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/tenants` | List tenants |
| POST | `/api/v1/tenants` | Create tenant + bootstrap subtree |
| DELETE | `/api/v1/tenants/{tenantId}` | Delete tenant and subtree |
| PUT | `/api/v1/tenants/{tenantId}/users/{username}` | Assign tenant to user |
| PUT | `/api/v1/tenants/{tenantId}/quotas` | Quotas: `maxDevices`, `maxObjects` (null = no limit) |
| GET | `/api/v1/tenants/{tenantId}/usage` | Current usage (devices / objects) |

Login response includes `tenantId` when assigned.

## Scope for operator (BL-125)

User with `tenant_id` in `platform_users` and **without** admin role:

- **Read:** `GET /api/v1/objects` — only `root`, `root.tenant`, `root.tenant.{id}.*`
- **Write:** create/update/delete blocked with `403` for paths outside tenant (including `root.platform.*`)

Admin sees and writes everything.

## Quotas (BL-126)

| Quota | Enforcement |
|-------|-------------|
| `maxObjects` | Objects under `root.tenant.{id}.platform.*` |
| `maxDevices` | Objects of type `DEVICE` in the same subtree |

Exceeded → `409 Conflict` on `POST /api/v1/objects`.

```http
PUT /api/v1/tenants/acme/quotas
{"maxDevices": 50, "maxObjects": 500}
```

## Web Console

Node **`root.tenant`** → **Tenants** panel: create tenant, assign to user.

## Limitations

- No billing / OIDC tenant mapping
- Shared `root.platform` remains for legacy demo (admin-only for tenant users)
- Federation and tenant are orthogonal mechanisms

## Isolation mode (BL-155)

| Property | Default | Description |
|----------|---------|-------------|
| `ispf.tenant.isolation-mode` | `logical` | `logical` — path namespaces; `hard` — per-tenant PostgreSQL schema |
| `ispf.tenant.schema-prefix` | `tenant_` | Schema prefix when `hard` (`tenant_acme` for tenant `acme`) |

Env: `ISPF_TENANT_ISOLATION_MODE=logical|hard`, `ISPF_TENANT_SCHEMA_PREFIX=tenant_`.

**Web Console:** System → Runtime settings → **Multi-tenant** tab (or quick toggle **Tenant isolation mode** on Integrations tab). Change requires **restart** of `ispf-server`.

### Logical (default)

One shared PostgreSQL schema; tenant data under `root.tenant.{id}.platform.*`. Write isolation enforced in API (`requirePathInScope`).

### Hard mode

On `POST /api/v1/tenants`:

1. `tenantId` validated as a valid PostgreSQL schema suffix: `[a-z][a-z0-9_]{0,62}` (final name ≤ 63 characters with prefix); reserved names and existing schemas rejected.
2. Response includes `schemaName` (`tenant_{id}` by default).
3. `TenantSchemaService` creates the schema and asserts it exists; delete drops the schema (`CASCADE`).
4. `runInTenantSchema` sets `search_path` for app-data style work — **platform object tables remain shared** until a dedicated routing cutover (A≠B row isolation still open).

**When to enable hard:** SaaS with schema-level isolation hooks, compliance, separate backup/restore per tenant. **Do not enable** for single-tenant on-prem without data migration.

### OIDC tenant claim

| Property | Env | Default |
|----------|-----|---------|
| `ispf.tenant.oidc-tenant-claim` | `ISPF_TENANT_OIDC_CLAIM` | `tenant_id` |

When the JWT carries this claim, `TenantScopeService` binds path visibility to `root.tenant.{id}.*` (admin bypass). Empty claim name disables mapping; local user→tenant assignment still applies.

### Comparison

| | Logical | Hard |
|---|---------|------|
| DB schema | Shared | Per tenant (provisioned; platform tables still shared) |
| Path scope | `root.tenant.{id}.*` | Same + schema provision/drop |
| OIDC tenant claim | `tenant_id` (configurable) | Same |
| Restart on toggle | Yes | Yes |

See also [federation](federation.md), [roadmap](roadmap.md).
