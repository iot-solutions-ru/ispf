# Модель объектов

## Иерархия

Все сущности платформы — **узлы дерева объектов** с dot-нотацией пути:

```
root
└── root.platform
    ├── root.platform.devices
    │   ├── root.platform.devices.demo-sensor-01
    │   └── root.platform.devices.snmp-localhost
    ├── root.platform.models
    ├── root.platform.dashboards
    │   └── root.platform.dashboards.demo-sensor
    ├── root.platform.security
    │   ├── root.platform.security.users
    │   │   ├── root.platform.security.users.admin
    │   │   └── root.platform.security.users.operator
    │   └── root.platform.security.roles
    │       ├── root.platform.security.roles.admin
    │       └── root.platform.security.roles.operator
    └── root.platform.workflows
        └── root.platform.workflows.demo-alarm-handler
```

Пути разделяются **точкой** (`.`), не слэшем. Резолвинг дочернего пути: `parentPath + "." + name`.

## Типы объектов (`ObjectType`)

| Тип | Назначение |
|-----|------------|
| `ROOT` | Корень дерева |
| `TENANT` | Арендатор (multi-tenancy, план) |
| `USER` | Пользователь |
| `DEVICE` | Устройство с драйвером |
| `DRIVER` | Экземпляр драйвера |
| `MODEL` | Определение модели |
| `DASHBOARD` | HMI-экран |
| `WORKFLOW` | BPMN-процесс |
| `ALERT` | Правило алерта (legacy object type) |
| `AGENT` | Edge agent |
| `CUSTOM` | Произвольный контейнер |
| `APPLICATION` | Прикладное приложение (bundle) |
| `REPORT` | SQL-отчёт приложения |

## Безопасность в дереве

Узлы `root.platform.security` создаются автоматически при старте и при изменении пользователей:

| Путь | Тип | Переменные |
|------|-----|------------|
| `...security.users.{username}` | `USER` | `username`, `displayName`, `roles`, `enabled` |
| `...security.roles.{role}` | `CUSTOM` | `roleName`, `description` |

CRUD пользователей — через `POST/PUT/DELETE /api/v1/security/users` (роль `admin`). Удаление узла `USER` в дереве также удаляет учётную запись.

## Состав объекта (`PlatformObject`)

Каждый узел содержит:

- **Метаданные:** `id`, `path`, `displayName`, `description`, `templateId`, `createdAt`
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

### DataRecord

Табличная структура со строками данных. Типичный формат одной строки для REST:

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
| `name` | Имя в объекте |
| `schema` | `DataSchema` |
| `readable` / `writable` | Права доступа |
| `bindingExpression` | CEL-выражение (опционально) |
| `value` | Текущий `DataRecord` |

### Вычисляемые привязки (CEL)

При изменении переменной `BindingEvaluator` пересчитывает зависимые binding-переменные.

Контекст выражения: `self.<variableName>.<field>`

Пример из `mqtt-sensor-v1`:

```
self.temperature.value > self.threshold.value
```

→ переменная `alarmActive` становится `true`.

Проверка выражения: `POST /api/v1/expressions/validate`.

## События

`EventDescriptor` задаёт имя, описание, схему payload и уровень (`DEBUG` … `CRITICAL`).

Публикация:

```http
POST /api/v1/events/fire
```

Событие валидируется по descriptor объекта, пишется в `event_history`, рассылается через WebSocket.

## Функции

`FunctionDescriptor` — имя, input/output `DataSchema`.

Вызов:

```http
POST /api/v1/objects/by-path/functions/invoke?path=...&name=...
Content-Type: application/json

{ "schema": {...}, "rows": [{...}] }
```

Встроенный handler: `acknowledgeAlarm` на `demo-sensor-01` (сброс `alarmActive`).

Расширение: реализуйте `FunctionHandler` в `ispf-server` и зарегистрируйте как Spring `@Component`.

## Модели (шаблоны)

Объекты создаются вручную или из **модели** (`templateId`). Модель задаёт набор переменных, событий, функций и bindings.

См. [MODELS.md](MODELS.md).

## Персистентность

Flyway-миграции (`packages/ispf-server/src/main/resources/db/migration/`):

| Таблица | Содержимое |
|---------|------------|
| `object_nodes` | Узлы дерева |
| `object_variables` | Значения и binding_expr |
| `event_history` | Журнал событий |
| `workflow_instances` | Экземпляры BPMN |
| `workflow_user_tasks` | Задачи оператора |
| `alert_rules` | Правила алертов |
| `event_correlators` | Корреляторы |

При старте `ObjectManager` загружает дерево из БД или выполняет `PlatformBootstrap` (пустая БД).

## Live-обновления

`ObjectChangeEvent` публикуется при создании/изменении/удалении объектов и переменных.

WebSocket `WS /ws/objects` рассылает JSON:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Web Console подписывается через `useObjectWebSocket` и инвалидирует TanStack Query cache.

## CRUD через API

| Операция | Endpoint |
|----------|----------|
| Список | `GET /api/v1/objects?parent=` |
| Получить | `GET /api/v1/objects/by-path?path=` |
| Редактор | `GET /api/v1/objects/by-path/editor?path=` |
| Создать | `POST /api/v1/objects` |
| Обновить | `PATCH /api/v1/objects/by-path?path=` |
| Удалить | `DELETE /api/v1/objects/by-path?path=` |
| Переменные | `GET/PUT .../variables` |

При создании `DASHBOARD` / `WORKFLOW` автоматически применяется соответствующая built-in модель.
