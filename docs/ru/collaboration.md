> **Язык:** русская версия (вычитка). Канонический английский: [en/collaboration.md](../en/collaboration.md).

﻿# Совместная работа (Этап 11)

Механизмы безопасной параллельной работы нескольких инженеров над одним деревом объектов ISPF.

## 11.1 Фундамент — доработка + аудит

### Оптимистический параллелизм

Каждый узел `object_nodes` имеет поле **`revision`** (монотонно растёт при конфигурационных изменениях).

| Заголовок | Значение |
|--------|----------|
| `If-Match` | Ожидаемая редакция при PATCH/PUT доступности, метаданных, функциях, событиях |
| `X-ISPF-Force: true` | Admin-only: перезапись без проверки revision |

При несоответствии → **409 Конфликт**:

```json
{
  "error": "REVISION_CONFLICT",
  "objectPath": "root.platform.devices.pump-01",
  "expectedRevision": 3,
  "currentRevision": 5,
  "changedBy": "ivan",
  "changedAt": "2026-06-21T12:00:00Z"
}
```

Версия **не** инкрементируется при телеметрии драйвера и расчетных привязках (`setDriverTelemetryValue`, `propagateBindings`).

### Аудит конфигурации

Таблица `object_config_audit` — журнал изменений metadata, variables, functions, events.

```
GET /api/v1/objects/by-path/audit?path=...&limit=50
```

Веб-консоль: вкладка **История изменений** в Редакторе свойств объекта.

### Устаревший редактор (веб-консоль)

WebSocket `/ws/objects` включает `revision` и `changedBy` в событиях `UPDATED` / `VARIABLE_UPDATED`.

Если редактор «грязный» и пришла ревизия выше базовой — баннер с **Перезагрузить** / **Перезаписать** (admin).

## 11.2 Живое сотрудничество

### Присутствие (мягкое)

Клиент шлёт по WS:

```json
{ "type": "presence", "path": "root.platform.devices", "username": "ivan", "mode": "edit" }
```

Сервер отвечает за списком активных пользователей на пути (TTL 30 с). Не блокирует запись.

### Аренда поддерева (жесткая блокировка)

```
POST   /api/v1/objects/leases   { "pathPrefix": "...", "ttlMinutes": 120 }
DELETE /api/v1/objects/leases?pathPrefix=...
GET    /api/v1/objects/leases
```

При активном lease другого держателя → **423 Locked** при записи.

### Объединение моделей

```
POST /api/v1/blueprints/merge-preview
POST /api/v1/blueprints/merge-apply
POST /api/v1/blueprints/{id}/upgrade-instances?dryRun=true
```

Предварительный просмотр показывает противоречия между двумя моделями; применение требует разрешения для каждого конфликта.

## 11.3 ACL владения поддеревом

Расширенные permission на `object_acl_entries`:

| Разрешение | Права |
|------------|-------|
| `OWNER` | read + write + invoke + grant ACL + lease |
| `EDITOR` | read + write + invoke |
| `VIEWER` | read |

Наследование вверх по path (как READ/WRITE/INVOKE). Назначение через `PUT /api/v1/objects/by-path/acl?path=<prefix>`.

## 11.4 Change-сеты и продвижение

```
POST /api/v1/platform/change-sets
GET  /api/v1/platform/change-sets
POST /api/v1/platform/change-sets/{id}/preview
POST /api/v1/platform/change-sets/{id}/apply?force=false
```

Change-set — именованный пакет ops с необязательным `expectedRevision` на каждый путь. Предварительный просмотр показывает конфликты; применять транзакционный (или принудительный).

Environment badge: `GET /api/v1/info` → `environment` (`ispf.environment`, default `dev`).

## Связанные документы

- [security](security.md) — RBAC и ACL
- [object-model](object-model.md) — дерево объектов
- [blueprints](blueprints.md) — разница/обновление моделей
- [roadmap](roadmap.md) — Фаза 11
