> **Язык:** русская версия (вычитка). Канонический английский: [en/api.md](../en/api.md).

﻿# REST API (v1)

Базовый URL: `http://localhost:8080`

Авторизация: JWT (Keycloak) или заголовок `X-ISPF-Role: admin|developer|operator` (профиль `local`).  
Матрица ролей: [security](security.md).

## Платформа

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/info` | public | Имя, версия, `javaVersion`, `springBootVersion`, `capabilities[]` |
| ПОЛУЧИТЬ | `/api/v1/platform/metrics` | админ | Сводные метрики платформы (время выполнения, БД, дерево, драйверы, подключения, безопасность, историк, автоматизация) |
| GET | `/api/v1/platform/haystack/export` | operator+ | Экспорт Haystack grid для поддерева (`rootPath`, `includePoints`) |
| GET | `/api/v1/platform/haystack/search` | operator+ | Поиск по тегам AND (`tags`, `rootPath`, `entityKind`, `limit`) |
| ПОЛУЧИТЬ | `/api/v1/platform/haystack/query` | оператор+ | Запрос фильтра Haystack (`filter`, `rootPath`, `entityKind`, `offset`, `limit`) — см. [0023-haystack-query-runtime](decisions/0023-haystack-query-runtime.md) |
| GET | `/api/v1/platform/update/status` | admin | Проверка обновлений с GitHub Releases |
| POST | `/api/v1/platform/update/check` | admin | Принудительная проверка релиза |
| POST | `/api/v1/platform/update/apply` | admin | Скачать релиз и перезапустить сервер (VPS, `apply-enabled=true`) |
| GET | `/api/v1/auth/me` | public | Principal и роли |
| POST | `/api/v1/expressions/validate` | admin | Валидация CEL |

**Запрос фильтра Haystack (BL-102):**

```http
GET /api/v1/platform/haystack/query?filter=point+and+temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

```json
{
  "formatVersion": 1,
  "filter": "point and temp",
  "count": 1,
  "matches": [
    {
      "entityKind": "point",
      "path": "root.platform.devices.lab-userA-01",
      "variableName": "sineWave",
      "tags": { "point": true, "temp": true, "sensor": true },
      "curVal": 0.42,
      "unit": "°C"
    }
  ]
}
```

Filter v1: marker conjunction with `and` (e.g. `equip and ahu`). Legacy tag search: `GET /platform/haystack/search?tags=equip&tags=temp`.

## Объекты

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/objects` | operator+ | Список (`?parent=` — дочерние) |
| GET | `/api/v1/objects/by-path` | operator+ | Объект по `path` |
| GET | `/api/v1/objects/by-path/editor` | operator+ | Объект + events + functions |
| POST | `/api/v1/objects` | admin | Создать объект |
| PATCH | `/api/v1/objects/by-path` | admin | displayName, description |
| DELETE | `/api/v1/objects/by-path` | admin | Удалить поддерево |
| PUT | `/api/v1/objects/reorder` | admin | Порядок дочерних (`parentPath`, `orderedPaths`) |
| GET | `/api/v1/objects/by-path/variables` | operator+ | Список переменных |
| GET | `/api/v1/objects/by-path/variables/detail` | operator+ | Одна переменная (`name`) |
| ПОЛУЧИТЬ | `/api/v1/objects/by-path/variables/history` | оператор+ | История переменная (`path`, `name`, `field`, `from`, `to`, `limit`; только при `historyEnabled`) |
| ПОЛУЧИТЬ | `/api/v1/objects/by-path/variables/history/aggregate` | оператор+ | Агрегаты (`bucket`, `from`, `to`, `limit` — до 2000 интервалов; `avg`/`min`/`max`/`count`) |
| ПОЛУЧИТЬ | `/api/v1/objects/by-path/variables/history/export` | оператор+ | Скачать историю (`format=csv\|json`, те же фильтры, `limit` до 10000) |
| PATCH | `/api/v1/objects/by-path/variables/history` | admin | `historyEnabled`, `historyRetentionDays` |
| PUT | `/api/v1/objects/by-path/variables` | admin | Записать значение |

### Создание объекта

```http
POST /api/v1/objects
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "pump-01",
  "type": "DEVICE",
  "displayName": "Pump 01",
  "description": "",
  "templateId": "mqtt-sensor-v1",
  "driverId": "mqtt",
  "autoApplyRelativeBlueprints": true
}
```

| Поле | Описание |
|------|----------|
| `templateId` | Явное применение модели (INSTANCE name или RELATIVE mixin) |
| `driverId` | Для `DEVICE`: provisioning драйвера после create |
| `autoApplyRelativeBlueprints` | По умолчанию `true`. ОТНОСИТЕЛЬНЫЕ миксины с **пустым** CEL не применяются; нужен непустой `suitabilityExpression` |

`mqtt-sensor-v1` — fixture-модель (`ispf.bootstrap.fixtures-enabled`). См. [blueprints](blueprints.md), [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

## функции

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| POST | `/api/v1/objects/by-path/functions/invoke` | operator+ | Вызов (`path`, `name`, body) |

## Дашборды

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/dashboards/by-path` | operator+ | title, layoutJson, refreshIntervalMs |
| PUT | `/api/v1/dashboards/by-path/layout` | admin | Сохранить layout JSON |
| PUT | `/api/v1/dashboards/by-path/title` | admin | Заголовок экрана |

## Рабочие процессы

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/workflows/by-path` | operator+ | BPMN, status, instance state |
| PUT | `/api/v1/workflows/by-path/bpmn` | admin | Сохранить BPMN XML |
| PUT | `/api/v1/workflows/by-path/status` | admin | `DRAFT` / `ACTIVE` / `STOPPED` |
| POST | `/api/v1/workflows/by-path/run` | admin | Запуск экземпляра |
| POST | `/api/v1/workflows/instances/{instanceId}/cancel` | operator+ | Отмена экземпляра |

## Приложения (REQ-PF)

Платформенный уровень для развертывания прикладных решений без Java в `ispf-server`.  
Полное описание: [applications](applications.md).

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| POST | `/api/v1/applications` | admin | Регистрация приложения |
| ПОСТ | `/api/v1/applications/{appId}/deploy` | админ | Bundle Deploy (миграции + функции + расписания + отчеты) |
| GET | `/api/v1/applications/{appId}/reports` | admin | Список SQL-отчётов |
| POST | `/api/v1/applications/{appId}/reports/deploy` | admin | Deploy одного отчёта |
| POST | `/api/v1/applications/{appId}/reports/{reportId}/run` | operator+ | Выполнить отчёт |
| GET | `/api/v1/applications/{appId}/reports/{reportId}/export` | operator+ | CSV-экспорт |
| POST | `/api/v1/applications/{appId}/data/migrate` | admin | SQL-миграции приложения |
| GET | `/api/v1/applications/{appId}/data/status` | admin | Статус миграций |
| POST | `/api/v1/applications/{appId}/functions/deploy` | admin | Deploy script-функции |
| POST | `/api/v1/bff/invoke` | operator+ | Универсальный BFF-шлюз к функциям |
| GET | `/api/v1/schedules` | admin | Список расписаний |
| POST | `/api/v1/schedules` | admin | Создать/обновить расписание |

Подробнее: [applications](applications.md), [reports](reports.md), [plugins](plugins.md).

## Рабочая очередь

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/work-queue` | operator+ | Открытые user tasks (`limit`) |
| POST | `/api/v1/work-queue/claim` | operator+ | `taskId`, `operatorId` |
| POST | `/api/v1/work-queue/complete` | operator+ | Завершить задачу |

## События

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/events` | operator+ | Журнал (`objectPath`, `limit` ≤ 200) |
| POST | `/api/v1/events/fire` | operator+ | Публикация события |

### Пожарное событие

```http
POST /api/v1/events/fire
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "eventName": "thresholdExceeded",
  "payload": {
    "schema": { "name": "payload", "fields": [...] },
    "rows": [{ "temperature": 95.0, "threshold": 80.0 }]
  }
}
```

## Правила оповещений

| Метод | Путь | Роли |
|--------|------|------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

## Корреляторы событий

| Метод | Путь | Роли |
|--------|------|------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

## Драйверы

| Метод | Путь | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/drivers` | operator+ | Каталог SPI-драйверов |
| GET | `/api/v1/drivers/runtime/status` | operator+ | Статус poll loop |
| POST | `/api/v1/drivers/runtime/start` | admin | Запуск (`devicePath`) |
| POST | `/api/v1/drivers/runtime/stop` | admin | Остановка |
| PUT | `/api/v1/drivers/runtime/configure` | admin | Конфиг + point mappings |

## Плагин моделей

| Метод | Путь | Роли |
|--------|------|------|
| GET | `/api/v1/blueprints` | admin |
| GET | `/api/v1/blueprints/{id}` | admin |
| GET | `/api/v1/blueprints/by-name/{name}` | admin |
| POST | `/api/v1/blueprints` | admin |
| PUT | `/api/v1/blueprints/{id}` | admin |
| DELETE | `/api/v1/blueprints/{id}` | admin |
| POST | `/api/v1/blueprints/{id}/apply?objectPath=` | admin |
| POST | `/api/v1/blueprints/{id}/instantiate` | admin |
| POST | `/api/v1/blueprints/from-object` | admin |
| GET | `/api/v1/blueprints/attachments` | admin |

## Привод

| Путь | Доступ |
|------|--------|
| `/actuator/health` | public |
| `/actuator/prometheus` | public |
| `/actuator/metrics` | authorized |

## Вебсокет

**Endpoint:** `WS /ws/objects`

Без аутентификации (dev). Сообщения при ремонте объектов:

```json
{
  "type": "VARIABLE_UPDATED | OBJECT_CREATED | OBJECT_DELETED | EVENT_FIRED | ...",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Клиент: `apps/web-console/src/hooks/useObjectWebSocket.ts`.

## Коды ошибок

Стандартные HTTP: `400` (проверка), `404` (ObjectNotFoundException), `403` (RBAC), `500`.
