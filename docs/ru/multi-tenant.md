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
        └── root.tenant.acme.platform
            ├── .devices
            ├── .dashboards
            └── .security
                ├── .users
                └── .roles
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

- Чтение/запись только `root.tenant.{id}.*` (+ `root` / `root.tenant` для навигации)
- Шаблоны ролей: префиксы `root.platform.*` → `root.tenant.{id}.platform.*`
- History/events: `requirePathInScope`

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

### Честный статус

| Слой | Статус |
|------|--------|
| Логический SaaS A≠B (path + API + role scope + tenant-admin) | **Готово** |
| OIDC claim `tenant_id` | **Готово** |
| Hard schema provision/drop | **Готово** (hooks) |
| Роутинг platform-таблиц по схемам (DB row A≠B) | **Опционально / открыто** |

Не заявлять row-level A≠B для `object_nodes`, пока нет отдельного table routing. Публикуемый SaaS сегодня — path+API + локальный `tenant-admin`.

См. также [federation](federation.md), [roadmap](roadmap.md), [security](security.md).
