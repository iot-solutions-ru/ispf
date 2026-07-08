> **Язык:** русская версия (вычитка). Канонический английский: [en/multi-tenant.md](../en/multi-tenant.md).

# Многопользовательские пространства имен (BL-125, BL-126).

Логическое разделение деревьев объектов по арендаторам в одном экземпляре ISPF.

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

**Принцип:** путь к объекту — пространство имен стабильного идентификатора; арендатор не учитывает HTTP-хост.

## API (администратор)

| Метод | Путь | Описание |
|--------|------|----------|
| GET | `/api/v1/tenants` | Список tenants |
| POST | `/api/v1/tenants` | Создать tenant + bootstrap поддерева |
| DELETE | `/api/v1/tenants/{tenantId}` | Удалить tenant и subtree |
| PUT | `/api/v1/tenants/{tenantId}/users/{username}` | Назначить tenant пользователю |
| PUT | `/api/v1/tenants/{tenantId}/quotas` | Квоты: `maxDevices`, `maxObjects` (null = без лимита) |
| GET | `/api/v1/tenants/{tenantId}/usage` | Текущее использование (devices / objects) |

Login response включает `tenantId`, если назначен.

## Прицел для оператора (БЛ-125)

Пользователь с `tenant_id` в `platform_users` и **без** роли admin:

- **Read:** `GET /api/v1/objects` — только `root`, `root.tenant`, `root.tenant.{id}.*`
- **Запись:** создание/обновление/удаление блокируется с `403` для путей вне арендатора (в т.ч. `root.platform.*`)

Админ видит и пишет всё.

## Квоты (BL-126)

| Квота | Правоприменение |
|-------|-------------|
| `maxObjects` | Объекты под `root.tenant.{id}.platform.*` |
| `maxDevices` | Объекты типа `DEVICE` в том же subtree |

Превышение → `409 Conflict` при `POST /api/v1/objects`.

```http
PUT /api/v1/tenants/acme/quotas
{"maxDevices": 50, "maxObjects": 500}
```

## Веб-консоль

Узел **`root.tenant`** → панель **Tenants**: создание tenant, назначение пользователю.

## Ограничения

- Нет выставления счетов/сопоставления арендаторов OIDC
- Shared `root.platform` остаётся для legacy demo (admin-only для tenant users)
- Федерация и арендатор — ортогональные механизмы.

## Режим изоляции (BL-155)

| Недвижимость | По умолчанию | Описание |
|----------|---------|----------|
| `ispf.tenant.isolation-mode` | `logical` | `logical` — path namespaces; `hard` — per-tenant PostgreSQL schema |
| `ispf.tenant.schema-prefix` | `tenant_` | Schema prefix when `hard` (`tenant_acme` for tenant `acme`) |

Env: `ISPF_TENANT_ISOLATION_MODE=logical|hard`, `ISPF_TENANT_SCHEMA_PREFIX=tenant_`.

**Веб-консоль:** Система → Настройки среды выполнения → вкладка **Мультиклиент** (или быстрое переключение **Режим изоляции арендатора** на вкладке «Интеграции»). Изменение требует **перезапуска** `ispf-server`.

### Логический (по умолчанию)

Одна общая схема PostgreSQL; данные арендатора под `root.tenant.{id}.platform.*`. Изоляция записи реализована в API (`requirePathInScope`).

### Сложный режим

При `POST /api/v1/tenants`:

1. `tenantId` наконец-то как допустимый суффикс схемы PostgreSQL: `[a-z][a-z0-9_]{0,62}` (итоговое имя ≤ 63 символов с префиксом).
2. Ответ включает `schemaName` (`tenant_{id}` по умолчанию).
3. `TenantSchemaService` создаёт схему `CREATE SCHEMA IF NOT EXISTS` (заглушка — маршрутизация источника данных).

**При включении жесткого уровня:** SaaS с жесткой изоляцией на уровне БД, соблюдением требований, резервным копированием и восстановлением для каждого клиента. **Не включать** для одноклиентской локальной среды без ограничения данных.

### Сравнение

| | Логический | Жесткий |
|---|---------|------|
| Схема БД | Общий | За арендатора |
| Path scope | `root.tenant.{id}.*` | Same + schema provision |
| Претензия арендатора OIDC | Планируется (продолжение BL-155) | Планируется |
| Перезапуск при переключении | Да | Да |

См. также [FEDERATION.md](federation.md), [ROADMAP.md](roadmap.md).
