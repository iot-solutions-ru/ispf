# Multi-tenant namespaces (PF spike)

Spike для Phase 4.2: логическое разделение деревьев по арендаторам в одном ISPF-инстансе.

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

Login response включает `tenantId`, если назначен.

## Scope для operator

Пользователь с `tenant_id` в `platform_users` и **без** роли admin при `GET /api/v1/objects` видит только:

- `root`, `root.tenant`, `root.tenant.{id}.*`

`root.platform.*` скрыт. Admin видит всё.

## Web Console

Узел **`root.tenant`** → панель **Tenants**: создание tenant, назначение пользователю.

## Ограничения spike

- Нет billing/quota
- Нет автоматического OIDC tenant mapping
- Shared `root.platform` остаётся для legacy demo
- Federation и tenant — ортогональные механизмы

См. также [FEDERATION.md](FEDERATION.md), [PLATFORM_DEVELOPER_BACKLOG.md §9](PLATFORM_DEVELOPER_BACKLOG.md).
