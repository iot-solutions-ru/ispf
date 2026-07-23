> **Язык:** русская версия (вычитка). Канонический английский: [en/api.md](../en/api.md).

# REST API (v1)

> **Статус:** Stable — справочник endpoints. Хаб: [doc-status.md](doc-status.md).

Base URL: `http://localhost:8080`

Аутентификация: **Bearer**-токен после `POST /api/v1/auth/login` (профиль `local` предзаполняет `admin`/`admin`; JWT/Keycloak — в других профилях). Опциональный заголовок `X-ISPF-Role` по умолчанию **выключен** (`ispf.security.local-role-header-enabled=false`).  
Матрица ролей: [security](security.md).

## Платформа

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/info` | public | Имя, версия, `javaVersion`, `springBootVersion`, `capabilities[]` |
| GET | `/api/v1/platform/metrics` | admin | Сводные метрики платформы (runtime, DB, tree, drivers, connections, security, historian, automation) |
| GET | `/api/v1/platform/haystack/export` | operator+ | Экспорт Haystack grid для поддерева (`rootPath`, `includePoints`) |
| GET | `/api/v1/platform/haystack/search` | operator+ | Поиск по тегам с AND (`tags`, `rootPath`, `entityKind`, `limit`) |
| GET | `/api/v1/platform/haystack/query` | operator+ | Запрос по Haystack-фильтру (`filter`, `rootPath`, `entityKind`, `offset`, `limit`) — см. [0023-haystack-query-runtime](decisions/0023-haystack-query-runtime.md) |
| GET | `/api/v1/platform/update/status` | admin | Проверка обновлений из GitHub Releases |
| POST | `/api/v1/platform/update/check` | admin | Принудительная проверка релиза |
| POST | `/api/v1/platform/update/apply` | admin | Скачать релиз и перезапустить сервер (VPS, `apply-enabled=true`) |
| GET | `/api/v1/auth/me` | public | Principal и роли |
| POST | `/api/v1/expressions/validate` | admin | Валидация CEL |

**Haystack filter query (BL-102):**

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

Filter v1: конъюнкция маркеров через `and` (напр. `equip and ahu`). Устаревший поиск по тегам: `GET /platform/haystack/search?tags=equip&tags=temp`.

## Объекты

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/objects` | operator+ | Список (`?parent=` — дочерние) |
| GET | `/api/v1/objects/by-path` | operator+ | Объект по `path` |
| GET | `/api/v1/objects/by-path/editor` | operator+ | Объект + events + functions |
| POST | `/api/v1/objects` | admin | Создать объект |
| PATCH | `/api/v1/objects/by-path` | admin | displayName, description |
| DELETE | `/api/v1/objects/by-path` | admin | Удалить поддерево |
| PUT | `/api/v1/objects/reorder` | admin | Порядок дочерних (`parentPath`, `orderedPaths`) |
| GET | `/api/v1/objects/by-path/variables` | operator+ | Список переменных |
| GET | `/api/v1/objects/by-path/variables/detail` | operator+ | Одна переменная (`name`) |
| GET | `/api/v1/objects/by-path/variables/history` | operator+ | История переменной (`path`, `name`, `field`, `from`, `to`, `limit`; только при `historyEnabled`) |
| GET | `/api/v1/objects/by-path/variables/history/aggregate` | operator+ | Агрегаты (`bucket`, `from`, `to`, `limit` — до 2000 buckets; `avg`/`min`/`max`/`count`; `dataSource`: `rollup`/`raw`/`none`) |
| GET | `/api/v1/objects/by-path/variables/history/export` | operator+ | Скачать историю (`format=csv\|json`, те же фильтры, `limit` до 10000) |
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
  "autoApplyMixinBlueprints": true
}
```

| Field | Description |
|-------|-------------|
| `templateId` | Явное применение blueprint (имя INSTANCE или MIXIN) |
| `driverId` | Для `DEVICE`: настройка драйвера после создания |
| `autoApplyMixinBlueprints` | По умолчанию `true`. MIXIN с **пустым** CEL не применяются; требуется непустой `suitabilityExpression` |

`mqtt-sensor-v1` — тестовый blueprint (`ispf.bootstrap.fixtures-enabled`). См. [blueprints](blueprints.md), [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

## Функции

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/api/v1/objects/by-path/functions/invoke` | operator+ | Вызов (`path`, `name`, body) |

## Дашборды

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/dashboards/by-path` | operator+ | title, layoutJson, refreshIntervalMs |
| PUT | `/api/v1/dashboards/by-path/layout` | admin | Сохранить layout JSON |
| PUT | `/api/v1/dashboards/by-path/title` | admin | Заголовок экрана |

## Workflows

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/workflows/by-path` | operator+ | BPMN, status, instance state |
| PUT | `/api/v1/workflows/by-path/bpmn` | admin | Сохранить BPMN XML |
| PUT | `/api/v1/workflows/by-path/status` | admin | `DRAFT` / `ACTIVE` / `STOPPED` |
| POST | `/api/v1/workflows/by-path/run` | admin | Запуск экземпляра |
| POST | `/api/v1/workflows/instances/{instanceId}/cancel` | operator+ | Отмена экземпляра |

## Applications (REQ-PF)

Платформенный слой для развёртывания прикладных решений без Java в `ispf-server`.  
Полное описание: [applications](applications.md).

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/api/v1/applications` | admin | Регистрация приложения |
| POST | `/api/v1/applications/{appId}/deploy` | admin | Развёртывание bundle (migrations + functions + schedules + reports) |
| GET | `/api/v1/applications/{appId}/reports` | admin | Список SQL-отчётов |
| POST | `/api/v1/applications/{appId}/reports/deploy` | admin | Развёртывание одного отчёта |
| POST | `/api/v1/applications/{appId}/reports/{reportId}/run` | operator+ | Выполнить отчёт |
| GET | `/api/v1/applications/{appId}/reports/{reportId}/export` | operator+ | Экспорт в CSV |
| POST | `/api/v1/applications/{appId}/data/migrate` | admin | SQL-миграции приложения |
| GET | `/api/v1/applications/{appId}/data/status` | admin | Статус миграций |
| POST | `/api/v1/applications/{appId}/functions/deploy` | admin | Развёртывание script-функции |
| POST | `/api/v1/bff/invoke` | operator+ | Универсальный BFF-шлюз к функциям |
| GET | `/api/v1/schedules` | admin | Список расписаний |
| POST | `/api/v1/schedules` | admin | Создать или обновить расписание |

См. также: [applications](applications.md), [reports](reports.md), [plugins](plugins.md).

## Work Queue

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/work-queue` | operator+ | Открытые задачи пользователя (`limit`) |
| POST | `/api/v1/work-queue/claim` | operator+ | `taskId`, `operatorId` |
| POST | `/api/v1/work-queue/complete` | operator+ | Завершить задачу |

## События

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/events` | operator+ | Журнал (`objectPath`, `limit` ≤ 200) |
| POST | `/api/v1/events/fire` | operator+ | Публикация события |

### Публикация события

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

## Alert Rules

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

## Event Correlators

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

## Drivers

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/drivers` | operator+ | Каталог SPI-драйверов |
| GET | `/api/v1/drivers/runtime/status` | operator+ | Статус цикла опроса |
| POST | `/api/v1/drivers/runtime/start` | admin | Запуск (`devicePath`) |
| POST | `/api/v1/drivers/runtime/stop` | admin | Остановка |
| PUT | `/api/v1/drivers/runtime/configure` | admin | Config + point mappings |

## Blueprints Plugin

| Method | Path | Roles |
|--------|------|-------|
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

## Platform analytics (BL-160, BL-206)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/platform/analytics/templates` | operator+ | Список шаблонов аналитики |
| GET | `/api/v1/platform/analytics/tags?path=` | operator+ | Список развёрнутых analytics tags (каталог, lineage) |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | operator+ | Запись каталога analytics tag |
| POST | `/api/v1/platform/analytics/templates/apply` | admin | Применить шаблон к устройству |
| POST | `/api/v1/platform/analytics/query` | operator+ | Согласованный агрегирующий запрос по нескольким тегам |
| POST | `/api/v1/platform/analytics/query/export?format=csv\|parquet` | operator+ | Экспорт результата запроса |
| POST | `/api/v1/platform/analytics/tags/backfill` | admin | Пересчёт окна производного тега |
| POST | `/api/v1/platform/analytics/rollups/rebuild` | admin | Пересборка материализованных rollups |
| GET | `/api/v1/platform/analytics/frames/active` | operator+ | Список активных event frames |
| POST | `/api/v1/platform/analytics/frames/open-shift` | admin | Открыть shift frame из MES `mes_oee_shift` |
| POST | `/api/v1/platform/analytics/frames/open` | admin | Открыть пользовательский event frame |
| POST | `/api/v1/platform/analytics/frames/close` | admin | Закрыть event frame |
| GET | `/api/v1/platform/analytics/frames/downtime-report` | operator+ | Минуты простоя по frame |
| GET | `/api/v1/platform/analytics/historian-sla` | operator+ | Снимок SLA запросов historian |
| GET | `/api/v1/platform/analytics/analytics-slo` | operator+ | Целевые SLO аналитической платформы (BL-210) |

### Multi-tag query

```http
POST /api/v1/platform/analytics/query
Content-Type: application/json

{
  "tags": [
    { "path": "root.platform.devices.demo-sensor-01", "variable": "temperature", "field": "value", "label": "temp" },
    { "path": "root.platform.devices.other", "variable": "pressure", "field": "value" }
  ],
  "from": "2026-07-01T00:00:00Z",
  "to": "2026-07-08T00:00:00Z",
  "bucket": "1h",
  "agg": "avg",
  "maxBuckets": 500
}
```

Ответ содержит согласованные `timestamps[]`, `values[]` по сериям (null, если bucket отсутствует), `dataSource` для каждой серии (`rollup` или `raw`) и `latencyMs`. Агрегат по одному тегу по-прежнему доступен через `GET .../history/aggregate` с полем `dataSource`.

Ограничения (настраиваемые): не более 20 тегов на запрос, таймаут 3 с, мягкий rate limit 120/мин.

### Analytics CEL expression (BL-211)

```http
POST /api/v1/platform/analytics/expression/validate
POST /api/v1/platform/analytics/expression/evaluate
Content-Type: application/json

{
  "expression": "avg(root.platform.devices.demo/temperature, 5m) * 2",
  "objectPath": "root.platform.devices.my-derived-tag",
  "asOf": "2026-07-09T10:00:00Z"
}
```

Поддерживаемые CEL-helpers (без префикса `hist.*`): `avg`, `min`, `max`, `last`, `sum`, `live`. См. [expression-language](expression-language.md). Для развёрнутых тегов задайте на устройстве `analyticsHelper` = `cel` и `analyticsExpression`; движок вычисляет значения по расписанию.

## Actuator

| Path | Access |
|------|--------|
| `/actuator/health` | public |
| `/actuator/prometheus` | public |
| `/actuator/metrics` | authorized |

## WebSocket

**Endpoint:** `WS /ws/objects`

Без аутентификации (dev). Сообщения при изменении объектов:

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

Стандартные HTTP: `400` (валидация), `404` (ObjectNotFoundException), `403` (RBAC), `500`.
