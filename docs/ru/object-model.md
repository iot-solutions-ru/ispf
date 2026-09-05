> **Язык:** русская версия (вычитка). Канонический английский: [en/object-model.md](../en/object-model.md).

# Модель объектов

> **Статус:** Stable — Дерево, переменные, события, функции. Теги: [doc-status](../en/doc-status.md).

Бизнес-логика ISPF выражается через **состав узлов дерева**: модели (blueprints), переменные, события, функции, workflow и узлы автоматизации. См. [architecture.md § Основной принцип](architecture.md).

## Иерархия

![Дерево объектов в Admin Explorer](../assets/ispf-object-tree.png)

Все сущности платформы — **узлы дерева объектов** с путями в точечной нотации:

```
root
└── root.platform
    ├── root.platform.devices
    │   ├── root.platform.devices.demo-sensor-01
    │   └── root.platform.devices.snmp-localhost
    ├── root.platform.mixin-blueprints
    ├── root.platform.instance-types
    ├── root.platform.singleton-blueprints
    ├── root.platform.instances
    ├── root.platform.dashboards
    │   └── root.platform.dashboards.demo-sensor
    ├── root.platform.reports
    │   └── root.platform.reports.ready-items
    ├── root.platform.security
    │   ├── root.platform.security.users
    │   │   ├── root.platform.security.users.admin
    │   │   └── root.platform.security.users.operator
    │   └── root.platform.security.roles
    │       ├── root.platform.security.roles.admin
    │       └── root.platform.security.roles.operator
    ├── root.platform.workflows
    │   └── root.platform.workflows.demo-alarm-handler
    ├── root.platform.alert-rules
    │   └── root.platform.alert-rules.temperature-threshold-exceeded
    └── root.platform.correlators
        └── root.platform.correlators.alarm-handler-on-threshold-event
```

Пути разделяются **точкой** (`.`), не слэшем. Дочерний путь: `parentPath + "." + name`.

## Типы объектов (`ObjectType`)

| Тип | Назначение |
|-----|------------|
| `ROOT` | Корень дерева |
| `TENANT` | Тенант (multi-tenancy, планируется) |
| `PLATFORM` | Узел `root.platform` |
| `DEVICES` | Каталог устройств |
| `DEVICE` | Устройство с драйвером |
| `DRIVER` | Экземпляр драйвера |
| `MODEL` | Определение модели |
| `DASHBOARDS` | Каталог дашбордов |
| `DASHBOARD` | HMI-экран |
| `WORKFLOWS` | Каталог workflow |
| `WORKFLOW` | BPMN-процесс |
| `ALERT_RULES` | Каталог alert rules |
| `ALERT` | Правило алерта |
| `CORRELATORS` | Каталог correlators |
| `CORRELATOR` | Коррелятор событий |
| `APPLICATIONS` | Каталог приложений |
| `APPLICATION` | Приложение (bundle) |
| `OPERATOR_APPS` | Каталог operator UI |
| `SECURITY` | Корень RBAC |
| `USERS` / `USER` | Пользователи |
| `ROLES` / `ROLE` | Роли |
| `FUNCTIONS` / `FUNCTION` | Функции приложения |
| `REPORTS` / `REPORT` | Каталог `root.platform.reports` и SQL-отчёты (`report-v1`) |
| `AGENT` | Edge-агент |
| `VISUAL_GROUP` | Визуальная группа — ссылки на объекты в `@groupMembers` без изменения их путей |
| `CUSTOM` | Произвольный контейнер (fallback для неизвестных узлов) |

Системные папки (`PLATFORM`, `DEVICES`, `ALERT_RULES`, …) получают семантический тип при bootstrap и через миграцию `V22__system_object_types.sql`. Пользовательские узлы — из шаблона модели или `CUSTOM`.

## Безопасность в дереве

Узлы под `root.platform.security` создаются автоматически при старте и при изменении пользователей:

| Путь | Тип | Переменные |
|------|-----|------------|
| `...security.users.{username}` | `USER` | `username`, `displayName`, `roles`, `enabled` |
| `...security.roles.{role}` | `CUSTOM` | `roleName`, `description` |

CRUD пользователей — через `POST/PUT/DELETE /api/v1/security/users` (роль `admin`). Удаление узла `USER` в дереве также удаляет учётную запись.

## Состав объекта (`PlatformObject`)

Каждый узел содержит:

- **Метаданные:** `id`, `path`, `displayName`, `description`, `templateId`, `sortOrder`, `createdAt`
- **Переменные** (`Variable`) — типизированные значения
- **Функции** (`FunctionDescriptor`) — вызываемые операции
- **События** (`EventDescriptor`) — публикуемые типы событий

## Типизированные данные

### DataSchema

Описание полей записи:

```json
{
  "name": "temperature",
  "fields": [
    { "name": "value", "type": "DOUBLE" },
    { "name": "unit", "type": "STRING" }
  ]
}
```

Типы полей (`FieldType`): `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `DATETIME`, `BINARY`, `RECORD`, `RECORD_LIST`.

### Качество телеметрии (BL-82)

Необязательное поле `quality` в строках телеметрии: `GOOD`, `UNCERTAIN`, `BAD` ([0025-telemetry-quality-flags](decisions/0025-telemetry-quality-flags.md)). Драйверы сопоставляют статус протокола (например, OPC UA StatusCode) с этими уровнями. Виджеты графиков пропускают сэмплы `BAD` (разрыв линии); колонка quality в historian — follow-up.

### DataRecord

Табличная структура со строками данных. Типичный однострочный формат REST:

```json
{
  "schema": { "name": "temperature", "fields": [...] },
  "rows": [{ "value": 23.5, "unit": "C" }]
}
```

Виджеты и API читают поле через `valueField` (по умолчанию `"value"`).

## Переменные

| Свойство | Описание |
|----------|----------|
| `name` | Имя на объекте |
| `schema` | `DataSchema` |
| `readable` / `writable` | Права доступа |
| `value` | Текущий `DataRecord` |

### Вычисляемые привязки (binding rules)

Правила на объекте в `@bindingRules` (см. [bindings](bindings.md)). Runtime — `BindingRuleEngine`; межобъектные — через активаторы и `read(remote/ref)`.

Краткий список platform bindings:

| Функция | Назначение |
|---------|------------|
| `selectField` | Поле source-переменной |
| `scale` | Линейное отображение диапазона |
| `clamp` | Ограничение min…max |
| `format` | Строка по шаблону `String.format` |
| `delta` | Δ к предыдущему сэмплу (stateful) |
| `counterRate` | B/s из Counter32 с wrap (stateful) |

Валидация: `POST /api/v1/expressions/validate`.

## События

`EventDescriptor` задаёт имя, описание, схему payload и уровень (`DEBUG` … `CRITICAL`).

Публикация:

```http
POST /api/v1/events/fire
```

Событие валидируется по дескриптору объекта, пишется в `event_history` и рассылается через WebSocket.

## Функции

`FunctionDescriptor` — имя, input/output `DataSchema`, опционально `sourceType` + `sourceBody`.

| `sourceType` | Поведение |
|--------------|-----------|
| *(пусто)* | Встроенный platform `FunctionHandler` по имени функции на объекте |
| `script` | JSON DSL (`steps`) — `ScriptFunctionHandler` |
| `java` | Исходник public-класса → `ObjectJavaFunction`; **компиляция при сохранении** (`PUT .../functions`), вызов через `JavaFunctionHandler` |

**Подробные примеры всех типов:** [object-functions](object-functions.md) (built-in handlers, script steps, Java, bindings, workflow, REST).

Вызов:

```http
POST /api/v1/objects/by-path/functions/invoke?path=...&name=...
Content-Type: application/json

{ "schema": {...}, "rows": [{...}] }
```

**Java-функция:** класс `implements com.ispf.core.function.ObjectJavaFunction`; компиляция при сохранении; см. [object-functions.md § Java](object-functions.md).

Встроенные handlers (имя функции на объекте + handler на сервере): `acknowledgeAlarm`, Virtual Lab (`calculate`, `fireEvent1`, …), Mini-TEC (`gpu_start`, …), `dispatchTelemetry` (MQTT gateway). Полный список и payload — в [object-functions](object-functions.md).

Расширение платформы: реализовать `FunctionHandler` в `ispf-server` и зарегистрировать как Spring `@Component`.

## Модели (шаблоны)

Объекты создаются вручную или из **модели** (`templateId`). Модель задаёт переменные, события, функции и bindings.

**MIXIN blueprints** при создании применяются автоматически только при **непустом** CEL (*Applicability condition*). Пустой CEL → только явное применение (`templateId`, API). Fixture blueprints (`mqtt-sensor-v1`, …) не входят в core registry.

**DEVICE + драйвер:** переменные `driver*` встраиваются при `provisionDriver()`, не через MIXIN auto-apply.

См. [blueprints](blueprints.md), [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

## Персистентность

Flyway-миграции (`packages/ispf-server/src/main/resources/db/migration/`):

| Таблица | Содержимое |
|--------|------------|
| `object_nodes` | Узлы дерева (включая alert rules и correlators) |
| `object_variables` | Значения переменных (`@bindingRules` — reserved var на объекте) |
| `variable_samples` | История телеметрии (time-series; Timescale hypertable — [0009-timescaledb-retention](decisions/0009-timescaledb-retention.md)) |
| `event_history` | Журнал событий (Timescale hypertable — [0015-event-history-timescale](decisions/0015-event-history-timescale.md)) |
| `workflow_instances` | Экземпляры BPMN |
| `workflow_user_tasks` | Задачи оператора |
| `correlator_hits` | Срабатывания correlators (runtime) |
| `alert_rules` / `event_correlators` | Legacy; мигрируются в дерево при старте |

При старте `ObjectManager` загружает дерево из БД или выполняет `PlatformBootstrap` (пустая БД).

## Live-обновления

`ObjectChangeEvent` публикуется при create/update/delete объектов и переменных.

WebSocket `WS /ws/objects` рассылает JSON:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Web Console подписывается через `useObjectWebSocket` и инвалидирует кэш TanStack Query.

## CRUD через API

| Операция | Endpoint |
|----------|----------|
| Список | `GET /api/v1/objects?parent=` |
| Получить | `GET /api/v1/objects/by-path?path=` |
| Редактор | `GET /api/v1/objects/by-path/editor?path=` |
| Создать | `POST /api/v1/objects` |
| Обновить | `PATCH /api/v1/objects/by-path?path=` |
| Удалить | `DELETE /api/v1/objects/by-path?path=` |
| Порядок дочерних | `PUT /api/v1/objects/reorder` (`parentPath`, `orderedPaths`) |
| Переменные | `GET/PUT .../variables` |

При создании `DASHBOARD` / `WORKFLOW` автоматически применяется соответствующая встроенная модель.

## История переменных

Текущее значение — в `object_variables`. **История** пишется в `variable_samples` только для переменных с `historyEnabled = true` при каждом `VARIABLE_UPDATED` (debounce по `min-interval-ms`).

У каждой переменной (в модели и на объекте):

| Поле | Описание |
|------|----------|
| `historyEnabled` | Писать ли временной ряд |
| `historyRetentionDays` | Срок хранения в днях; `null` — default платформы из `ispf.variable-history.retention-days` |

```http
GET /api/v1/objects/by-path/variables/history?path=...&name=temperature&field=value&limit=500
PATCH /api/v1/objects/by-path/variables/history?path=...&name=temperature
Content-Type: application/json

{"historyEnabled": true, "historyRetentionDays": 30}
```

Конфиг платформы: `ispf.variable-history` в `application.yml` (`enabled`, `min-interval-ms`, `retention-days`).

Графики (`useTrendSeries`) загружают историю с сервера и дополняют live-точками через WebSocket/polling.

Подробный roadmap: [variable-history](variable-history.md).
