> **Язык:** русская версия. Английский оригинал: [en/object-model.md](../en/object-model.md).

# Модель объектов

> **Статус:** Stable — дерево объектов, переменные, события, функции и хранение данных. Метки зрелости: [doc-status.md](doc-status.md).

Все сущности в ISPF — подключённые устройства, узлы расчётов, экраны оператора, мнемосхемы, правила тревог, процессы BPMN, отчёты и учётные записи — организованы в **единое дерево объектов**. Каждый узел дерева адресуется по пути через точку и работает через один и тот же набор API и WebSocket-уведомлений.

См. также: [Как устроен ISPF](learn.md) · [Архитектура](architecture.md) · [Шаблоны объектов (Blueprints)](blueprints.md) · [Правила расчёта (привязки)](bindings.md).

**Пример:** встроенный `demo-sensor-01` (идёт с сервером); минимальное дерево в пакете — [`examples/platform-primitive`](../../examples/platform-primitive/).

---

## Иерархия дерева и пути

![Дерево объектов в консоли администратора](../assets/ispf-object-tree.png)

Сегменты пути разделяются **точкой** (`.`), а не косой чертой: дочерний адрес образуется как `путьРодителя + "." + имяУзла`.

```text
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
    ├── root.platform.mimics
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

---

## Типы узлов (`ObjectType`)

Каждому узлу при создании назначается тип (`ObjectType`), определяющий его роль и редактор в консоли:

| Тип (`ObjectType`) | Назначение |
|--------------------|------------|
| `ROOT` / `PLATFORM` | Корень дерева (`root`) и системный раздел `root.platform` |
| `TENANT` | Изолированный контур организации при мультитенантности |
| `DEVICES` / `DEVICE` | Каталог устройств и конкретное устройство с протокольным драйвером |
| `DRIVER` | Служебный экземпляр драйвера |
| `MODEL` | Шаблон объекта (blueprint) в каталогах моделей |
| `DASHBOARDS` / `DASHBOARD` | Каталог дашбордов и экран оператора |
| `MIMICS` / `MIMIC` | Каталог мнемосхем и векторная мнемосхема SCADA |
| `WORKFLOWS` / `WORKFLOW` | Каталог маршрутов и исполняемый процесс BPMN |
| `ALERT_RULES` / `ALERT` | Каталог тревог и правило контроля порога/условия |
| `CORRELATORS` / `CORRELATOR` | Каталог корреляторов и правило связки событий по окну времени |
| `APPLICATIONS` / `APPLICATION` | Каталог установленных решений и паспорт прикладного пакета |
| `OPERATOR_APPS` | Настройки меню и стартовых экранов рабочих мест оператора |
| `REPORTS` / `REPORT` | Каталог `root.platform.reports` и SQL-отчёт |
| `SECURITY`, `USERS` / `USER`, `ROLES` / `ROLE` | Пользователи и роли доступа |
| `FUNCTIONS` / `FUNCTION` | Каталог и узел прикладной функции |
| `AGENT` | Удалённый периферийный агент (edge) |
| `VISUAL_GROUP` | Визуальная папка со ссылками на объекты (`@groupMembers`) без смены их реальных путей |
| `CUSTOM` | Произвольная папка или контейнер общего назначения |

> **Разделение ролей:** узлы `DEVICE` используются только для связи с оборудованием. Сводные расчёты, функции и логику цифровых двойников размещают на узлах шаблонов **`SINGLETON`** или **`INSTANCE`** — см. [Правила архитектуры приложений](application-principles.md).

---

## Из чего состоит объект (`PlatformObject`)

Любой узел дерева может содержать четыре группы элементов:

1. **Паспортные свойства:** `id`, `path`, `displayName`, `description`, `templateId`, `sortOrder`, `createdAt`.
2. **Переменные (`Variable`)** — именованные типизированные значения (показания приборов, уставки, настройки, расчётные показатели).
3. **Функции (`FunctionDescriptor`)** — вызываемые операции с входной и выходной схемой параметров.
4. **События (`EventDescriptor`)** — объявленные типы уведомлений и тревог, которые может публиковать объект.

---

## Типизированные данные (`DataSchema` и `DataRecord`)

Значение любой переменной в ISPF хранится не как «сырая строка», а как типизированная табличная структура **`DataRecord`**, описанная схемой полей **`DataSchema`**.

### Схема полей (`DataSchema`)

```json
{
  "name": "temperature",
  "fields": [
    { "name": "value", "type": "DOUBLE" },
    { "name": "unit", "type": "STRING" }
  ]
}
```

Поддерживаемые типы полей (`FieldType`): `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `DATETIME`, `BINARY`, `RECORD`, `RECORD_LIST`.

### Запись значения (`DataRecord`)

Для обычного датчика или уставки `DataRecord` содержит одну строку (`rows` из одного элемента):

```json
{
  "schema": {
    "name": "temperature",
    "fields": [
      { "name": "value", "type": "DOUBLE" },
      { "name": "unit", "type": "STRING" }
    ]
  },
  "rows": [
    { "value": 23.5, "unit": "C" }
  ]
}
```

Виджеты дашборда и формулы по умолчанию читают поле `"value"` (при необходимости имя поля уточняется свойством `valueField`).

<a id="telemetry-quality-bl-82"></a>

### Флаг достоверности телеметрии (`quality`)

Строка телеметрии может содержать необязательное поле `quality` со значениями `GOOD` (достоверно), `UNCERTAIN` (сомнительно) или `BAD` (недостоверно / обрыв датчика) — см. [ADR-0025](decisions/0025-telemetry-quality-flags.md).
- Протокольные драйверы (например, OPC UA) автоматически переводят коды статуса прибора в этот флаг.
- Графики на дашбордах не соединяют точки со статусом `BAD` сплошной линией, наглядно показывая разрыв измерения.
- Архив истории в PostgreSQL/Timescale и ClickHouse сохраняет флаг `quality` вместе со значением ([ADR-0074](decisions/0074-historian-quality-persistence.md)). Значение `null` означает старую запись без метки качества, а не `GOOD`.

---

## Переменные и правила расчёта

Основные свойства переменной:
- `name` — имя переменной внутри объекта (например, `temperature`);
- `schema` — схема полей `DataSchema`;
- `readable` / `writable` — разрешено ли чтение и запись;
- `value` — текущее значение `DataRecord`.

### Автоматический пересчёт (`@bindingRules`)

На объекте можно задать правила расчёта (*binding rules*), которые пересчитывают переменные по формуле CEL или встроенной функции при изменении входных данных либо по расписанию:

| Встроенная функция | Что делает |
|--------------------|------------|
| `selectField` | Извлекает отдельное поле из составной переменной |
| `scale` | Линейно пересчитывает шкалу (например, 4–20 мА → 0–10 бар) |
| `clamp` | Ограничивает число диапазоном `min…max` |
| `format` | Формирует строку по шаблону (`String.format`) |
| `delta` | Считает разницу с предыдущим измерением |
| `counterRate` | Переводит нарастающий счётчик (например, `Counter32` в SNMP) в скорость в секунду |

Проверить синтаксис формулы можно через `POST /api/v1/expressions/validate` или кнопкой проверки в инспекторе. Подробное описание: [Правила расчёта (привязки)](bindings.md) и [Справочник языка выражений](expression-language.md).

---

## События

Дескриптор `EventDescriptor` задаёт на объекте имя события, схему его полей и уровень важности (`DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`).

При возникновении события (от правила тревоги, функции или запроса `POST /api/v1/events/fire`) сервер проверяет данные по дескриптору, рассылает уведомление подписчикам WebSocket, передаёт его корреляторам и (если включён журнал) сохраняет в `event_history`.

---

## Функции на объектах

`FunctionDescriptor` объявляет вызываемую операцию на узле дерева: имя, входную и выходную схемы `DataSchema`, а также способ реализации (`sourceType`):

| `sourceType` | Как исполняется функция |
|--------------|-------------------------|
| *(пусто)* | Встроенный серверный обработчик Java по имени функции (`acknowledgeAlarm`, `dispatchTelemetry` и др.) |
| `script` | Декларативный JSON-скрипт из шагов (`selectMany`, `exec`, `setVar`, `return`) для работы с БД и переменными |
| `java` | Исходный код класса `ObjectJavaFunction`, компилируемый сервером при сохранении в песочнице |

Вызов по прямому пути объекта:

```http
POST /api/v1/objects/by-path/functions/invoke?path=root.platform.devices.demo-sensor-01&name=acknowledgeAlarm
Content-Type: application/json

{ "schema": { "name": "in", "fields": [] }, "rows": [{}] }
```

Примеры всех видов функций собраны в документе [Функции на объектах](object-functions.md).

---

## Шаблоны объектов (`Blueprints`)

Объекты создаются либо вручную, либо по **шаблону** (`templateId`), который сразу наделяет узел нужными переменными, событиями, функциями и формулами:
- **`SINGLETON`** и **`INSTANCE`** задают основную структуру узлов логики и оборудования.
- **`MIXIN`** (примесь) подключается к объектам дополнительно; автоматическое подключение при создании срабатывает только если у примеси явно задано непустое условие применимости на CEL (*Applicability condition*).
- Переменные настройки драйвера (`driver*`) на узлах `DEVICE` создаются автоматически при привязке драйвера (`provisionDriver()`).

Подробнее: [Шаблоны объектов (Blueprints)](blueprints.md).

---

## Пользователи в дереве объектов

Узлы в разделе `root.platform.security` автоматически отражают список пользователей и ролей системы:
- `root.platform.security.users.<логин>` (`USER`) — учётная запись (`username`, `displayName`, `roles`, `enabled`);
- `root.platform.security.roles.<роль>` — описание роли.

Управление учётными записями выполняется через `POST/PUT/DELETE /api/v1/security/users` (требуется роль `admin`); удаление узла `USER` в дереве удаляет соответствующего пользователя.

---

## Хранение в базе данных и живые обновления

Основные системные таблицы СУБД (управляются миграциями Flyway при старте сервера):

| Таблица | Что хранит |
|---------|------------|
| `object_nodes` | Иерархия и свойства всех узлов дерева |
| `object_variables` | Текущие значения переменных и правила `@bindingRules` |
| `variable_samples` | Архив временных рядов телеметрии (в PostgreSQL — гипертаблица TimescaleDB) |
| `event_history` | Журнал событий |
| `workflow_instances`, `workflow_user_tasks` | Состояние запущенных процессов BPMN и очередь задач операторов |
| `correlator_hits` | Текущие срабатывания корреляторов событий |

### Обновления по WebSocket

При любом изменении объектов или значений переменных сервер рассылает сообщение в `WS /ws/objects`:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Веб-консоль и экраны оператора подписаны на этот канал и обновляют показания без ручной перезагрузки страницы.

### Основные методы REST API для работы с деревом

| Операция | Запрос |
|----------|--------|
| Получить дочерние узлы | `GET /api/v1/objects?parent=root.platform.devices` |
| Получить объект по пути | `GET /api/v1/objects/by-path?path=...` |
| Создать узел | `POST /api/v1/objects` |
| Изменить свойства узла | `PATCH /api/v1/objects/by-path?path=...` |
| Удалить узел | `DELETE /api/v1/objects/by-path?path=...` |
| Изменить порядок соседних узлов | `PUT /api/v1/objects/reorder` |
| Прочитать / записать переменные | `GET / PUT /api/v1/objects/by-path/variables?path=...` |

---

## Запись истории переменных

Текущее значение каждой переменной всегда лежит в `object_variables`. Запись временного ряда в архив (`variable_samples` и/или ClickHouse) включается индивидуально для нужных переменных флагом **`historyEnabled = true`** (с защитой от слишком частой записи через `min-interval-ms` и сроком хранения `historyRetentionDays`).

```http
GET /api/v1/objects/by-path/variables/history?path=root.platform.devices.demo-sensor-01&name=temperature&field=value&limit=500

PATCH /api/v1/objects/by-path/variables/history?path=root.platform.devices.demo-sensor-01&name=temperature
Content-Type: application/json

{"historyEnabled": true, "historyRetentionDays": 30}
```

Подробно об архиве и агрегациях: [История переменных](variable-history.md) и [Уровни хранения истории](historian-tiers.md).
