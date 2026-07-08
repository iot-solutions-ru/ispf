# Multi-tenant namespaces (BL-125, BL-126)

Logical separation of object trees by tenant in a single ISPF instance.

## Структура путей

```text
root
├── root.platform          ← shared / default (legacy demo)
└── root.tenant
    └── root.tenant.acme
        └── root.tenant.acme.platform
            ├── .devices
            └── .dashboards
```

**Принцип:** object path — стабильный идентификатор namespace; tenant не равен HTTP-host.

## API (admin)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/tenants` | Список tenants |
| POST | `/api/v1/tenants` | Создать tenant + bootstrap поддерева |
| DELETE | `/api/v1/tenants/{tenantId}` | Удалить tenant и subtree |
| PUT | `/api/v1/tenants/{tenantId}/users/{username}` | Назначить tenant пользователю |
| PUT | `/api/v1/tenants/{tenantId}/quotas` | Квоты: `maxDevices`, `maxObjects` (null = без лимита) |
| GET | `/api/v1/tenants/{tenantId}/usage` | Текущее использование (devices / objects) |

Login response включает `tenantId`, если назначен.

## Scope для operator (BL-125)

Пользователь с `tenant_id` в `platform_users` и **без** роли admin:

- **Read:** `GET /api/v1/objects` — только `root`, `root.tenant`, `root.tenant.{id}.*`
- **Write:** create/update/delete блокируются с `403` для путей вне tenant (в т.ч. `root.platform.*`)

Admin видит и пишет всё.

## Quotas (BL-126)

| Quota | Enforcement |
|-------|-------------|
| `maxObjects` | Объекты под `root.tenant.{id}.platform.*` |
| `maxDevices` | Объекты типа `DEVICE` в том же subtree |

Превышение → `409 Conflict` при `POST /api/v1/objects`.

```http
PUT /api/v1/tenants/acme/quotas
{"maxDevices": 50, "maxObjects": 500}
```

## Web Console

Узел **`root.tenant`** → панель **Tenants**: создание tenant, назначение пользователю.

## Ограничения

- Нет billing / OIDC tenant mapping
- Shared `root.platform` остаётся для legacy demo (admin-only для tenant users)
- Federation и tenant — ортогональные механизмы

## Isolation mode (BL-155)

| Property | Default | Описание |
|----------|---------|----------|
| `ispf.tenant.isolation-mode` | `logical` | `logical` — path namespaces; `hard` — per-tenant PostgreSQL schema |
| `ispf.tenant.schema-prefix` | `tenant_` | Schema prefix when `hard` (`tenant_acme` for tenant `acme`) |

Env: `ISPF_TENANT_ISOLATION_MODE=logical|hard`, `ISPF_TENANT_SCHEMA_PREFIX=tenant_`.

**Web Console:** System → Runtime settings → **Multi-tenant** tab (или quick toggle **Tenant isolation mode** на вкладке Integrations). Изменение требует **перезапуска** `ispf-server`.

### Logical (default)

Один shared PostgreSQL schema; tenant data under `root.tenant.{id}.platform.*`. Write isolation enforced in API (`requirePathInScope`).

### Hard mode

При `POST /api/v1/tenants`:

1. `tenantId` проверяется как допустимый суффикс PostgreSQL schema: `[a-z][a-z0-9_]{0,62}` (итоговое имя ≤ 63 символов с префиксом).
2. Ответ включает `schemaName` (`tenant_{id}` по умолчанию).
3. `TenantSchemaService` создаёт schema `CREATE SCHEMA IF NOT EXISTS` (stub — routing datasource follow-up).

**Когда включать hard:** SaaS с жёстким DB-level isolation, compliance, отдельные backup/restore per tenant. **Не включать** для single-tenant on-prem без миграции данных.

### Сравнение

| | Logical | Hard |
|---|---------|------|
| DB schema | Shared | Per tenant |
| Path scope | `root.tenant.{id}.*` | Same + schema provision |
| OIDC tenant claim | Planned (BL-155 follow-up) | Planned |
| Restart on toggle | Yes | Yes |

См. также [FEDERATION.md](FEDERATION.md), [ROADMAP.md](ROADMAP.md).
