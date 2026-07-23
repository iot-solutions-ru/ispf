> **Язык:** русская версия (вычитка). Канонический английский: [en/multi-tenant.md](../en/multi-tenant.md).

# Мультитенантный SaaS (BL-125, BL-126, BL-155)

Логическое разделение деревьев объектов по тенантам в одном экземпляре ISPF с локальным владельцем **tenant-admin** на ветку клиента.

## Акторы

| Актор | Роль | `tenant_id` | Возможности |
|-------|------|-------------|-------------|
| Platform ops | `admin` | **NULL** (обязательно) | Создание/удаление тенантов, глобальная безопасность, видит всё. Только global admin обходит `TenantScopeService`. |
| Локальный админ тенанта | `tenant-admin` | обязателен | Полный владелец `root.tenant.{id}.*`: CRUD в ветке, пользователи/роли тенанта, квоты/usage. **Нельзя** создавать тенанты, видеть чужие, ходить в `root.platform.*`, выдавать глобальный `admin`. |
| Пользователь тенанта | `operator` / `developer` / custom | обязателен | Ветка тенанта + ACL |

**Критическое правило:** роль `admin` при ненулевом `tenant_id` **запрещена**. Global admin всегда с `tenant_id` NULL. `tenant-admin` никогда не обходит tenant scope.

## Структура путей

```text
root
├── root.platform                    ← shared / default (legacy; global admin)
└── root.tenant
    └── root.tenant.acme
        └── root.tenant.acme.platform   ← базовый каталог (как у global platform)
            ├── .security / .users / .roles
            ├── .devices, .alert-rules, .operator-apps
            ├── .dashboards, .mimics, blueprints, .reports
            ├── .correlators, .workflows, .queries, .event-filters, .process-programs
            ├── .schedules, .data-sources, .bindings, .migrations
            └── .applications, .instances
            (MES / marketplace не пресидятся — тенант ставит решения сам)
```

**Принцип:** путь объекта — стабильный идентификатор namespace; тенант не привязан к HTTP-хосту.

## Создание тенанта + локальный админ

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

По умолчанию: `adminUsername` = `{tenantId}-admin`, пароль генерируется. В ответе — **одноразовые** `adminUsername` и `adminPassword`. Bootstrap: дерево platform, `security/users/roles`, пользователь `tenant-admin` с `tenant_id`, OWNER ACL на `root.tenant.{id}` (+ EDITOR для operator/developer).

## API

| Метод | Путь | Кто | Описание |
|--------|------|-----|----------|
| GET | `/api/v1/tenants` | global `admin` | Список |
| POST | `/api/v1/tenants` | global `admin` | Создать + bootstrap + local admin |
| GET | `/api/v1/tenants/{tenantId}` | global `admin` или свой `tenant-admin` | Карточка |
| DELETE | `/api/v1/tenants/{tenantId}` | global `admin` | Удалить |
| PUT | `/api/v1/tenants/{tenantId}/users/{username}` | global `admin` | Назначить tenant |
| PUT | `/api/v1/tenants/{tenantId}/quotas` | global `admin` или свой `tenant-admin` | Квоты |
| GET | `/api/v1/tenants/{tenantId}/usage` | global `admin` или свой `tenant-admin` | Usage |
| `/**` | `/api/v1/security/users/**`, `/roles/**` | `admin` или `tenant-admin` | Скоуп в сервисах |

Cluster / license / federation / audit — только global admin.

## Изоляция

Пользователь с `tenant_id` без global `admin`:

- **Storage** остаётся `root.tenant.{id}.platform.*`
- **Sole-tenant / white-label API:** запросы и ответы используют `root` / `root.platform.*` как единственный мир (`TenantVirtualRoot`). Узлы `root.tenant` / `root.tenant.{id}` скрыты.
- Запись вне своей platform-ветки (включая чужие canonical-пути) → `403`
- Шаблоны ролей: префиксы `root.platform.*` → `root.tenant.{id}.platform.*`
- History/events/WebSocket — тот же expand/collapse

Global `admin` видит **canonical** пути без rewrite. `tenant-admin` — OWNER в своей ветке.

## Локальная platform DB запрещена для тенантов

Любой пользователь с ненулевым `tenant_id` (включая **tenant-admin**) **не может** использовать локальную/platform БД. Разрешены только **внешние** JDBC data sources (удалённые БД). Global `admin` без изменений.

| Правило | Для tenant-вызывающих |
|---------|------------------------|
| `connectionMode=internal` | Запрещено (create / update / test / execute) |
| External JDBC URL | Отклонять localhost, `127.0.0.0/8`, `::1`, link-local, private-диапазоны (`10/8`, `172.16/12`, `192.168/16`, IPv6 ULA) и хост из `spring.datasource.url`. Allowlist драйверов без изменений. Private-диапазоны можно разрешить через `ispf.tenant.datasources.allow-private-addresses=true` (`ISPF_TENANT_DS_ALLOW_PRIVATE_ADDRESSES`) для OT/LAN-деплоев. |
| Script / BFF SQL с пустым `dataSourcePath` | Запрещено (fallback на platform catalog) |
| Migrations / SQL bindings / reports | `dataSourcePath` — только разрешённый external DS |

Внешние DS тенанта: sole-tenant путь `root.platform.data-sources` (storage: `root.tenant.{id}.platform.data-sources`). Guard: `TenantLocalDataAccessGuard`.

## Квоты (BL-126)

| Квота | Где |
|-------|-----|
| `maxObjects` | под `root.tenant.{id}.platform.*` |
| `maxDevices` | `DEVICE` в том же subtree |

Превышение → `409` на `POST /api/v1/objects`.

## Режим изоляции (BL-155)

| Свойство | По умолчанию | Описание |
|----------|--------------|----------|
| `ispf.tenant.isolation-mode` | `logical` | path+API; `hard` — ещё schema provision/drop |
| `ispf.tenant.schema-prefix` | `tenant_` | префикс схемы |
| `ispf.tenant.oidc-tenant-claim` | `tenant_id` | JWT claim (OIDC) |
| `ispf.tenant.db-row-isolation` | `true` | PostgreSQL RLS (`app.tenant_id` / `app.tenant_bypass`) на shared object-таблицах. На H2 — no-op. |

Env: `ISPF_TENANT_ISOLATION_MODE`, `ISPF_TENANT_SCHEMA_PREFIX`, `ISPF_TENANT_OIDC_CLAIM`, `ISPF_TENANT_DB_ROW_ISOLATION`.

### Изоляция строк БД (PostgreSQL RLS)

При `ispf.tenant.db-row-isolation=true` (по умолчанию) на PostgreSQL:

- Миграция `V86__tenant_row_level_security.sql` включает **FORCE ROW LEVEL SECURITY** на path-таблицах (`object_nodes`, `object_variables`, `object_acl_entries`, `event_history`, `variable_samples`, `object_config_audit`, `object_edit_leases`, `alarm_shelves`, `alarm_shelf_requests`).
- На запрос: global admin / без auth → `app.tenant_bypass=on`; пользователь тенанта → `bypass=off` + `app.tenant_id=<id>`.
- Незаданные GUC = default-allow (Flyway / bootstrap / ObjectTreeLoad).
- H2 **не** применяет RLS (Flyway пропускает V86 на H2).

### Честный статус

| Слой | Статус |
|------|--------|
| Логический SaaS A≠B (path + API + role scope + tenant-admin) | **Готово** |
| Sole-tenant / white-label virtual root (`root.platform.*`) | **Готово** (`TenantVirtualRoot`) |
| OIDC claim `tenant_id` | **Готово** |
| Hard schema provision/drop | **Готово** (hooks) |
| DB row A≠B на shared platform-таблицах | **RLS готово** при `ispf.tenant.db-row-isolation=true` (PostgreSQL); физический schema split по-прежнему опционален |

См. также [federation](federation.md), [roadmap](roadmap.md), [security](security.md).
