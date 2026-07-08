> **Язык:** русская версия (вычитка). Канонический английский: [en/blueprints.md](../en/blueprints.md).

﻿# Плагин Blueprints

Модуль `ispf-plugin-blueprint` — система **blueprint** (шаблоны структуры объектов). Один движок, три вида в дереве и API.

## Три вида моделей (`BlueprintType`)

| Тип | Каталог | Поведение |
|-----|---------|-----------|
| `RELATIVE` | `root.platform.relative-blueprints` | Необязательные примеси — переменные/события/функции **вливаются** в существующий объект |
| `INSTANCE` | `root.platform.instance-types` | Шаблон **типа объекта** — создание экземпляров через экземпляр |
| `ABSOLUTE` | `root.platform.absolute-blueprints` | Singleton blueprint — один живой объект в `root.platform.instances.*` |

**Внутренние схемы** (1:1 с `ObjectType`: `DATA_SOURCE`, `SCHEDULE`, `DASHBOARD`, …) хранятся в реестре для начальной загрузки, но **не отображаются** в каталоге относительных чертежей и **не используются** в `appliedBlueprintIds`. Структура вшивается в примере через `*ObjectService.ensureStructure()`.

См. [0011](decisions/0011-model-type-semantics.md).

### Связь с объектом

- `templateId` — primary model (INSTANCE/ABSOLUTE)
- `appliedBlueprintIds` — JSON-массив id всех применённых моделей (persisted)
- При слиянии коллизий имён: **последние победы + предупреждение** (не ошибка)

### Автоматическое применение ОТНОСИТЕЛЬНОГО

При `POST /objects` с `autoApplyRelativeBlueprints=true` (default) вызывается `BlueprintEngine.applyRelativeModels()`.

**Условия автоматически применяются** (все обязательно):

1. `BlueprintType.RELATIVE`, не системный
2. `targetObjectType` соответствует типу объекта
3. **`suitabilityExpression` (Условие применимости, CEL) не пустой** и остается в `true`
4. Модель ещё не в `appliedBlueprintIds`

**Пустой CEL** → модель **никогда** не применяется автоматически. Для миксина без условий воспользуйтесь явным применением: `templateId` при создании, `POST /api/v1/relative-blueprints/{id}/apply`, модели-компаньоны в комплекте.

**Явный применить** (шаблон/API): вчерася `targetObjectType`; CEL optionalen — если задано, должно быть `true`.

См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

### Условие применимости (CEL)

Поле `suitabilityExpression` в `BlueprintDefinition` — в Web Console: *Applicability condition (CEL)*.

| Значение | Автоматическое применение | Явный подать заявку |
|----------|------------|-------------|
| пусто | нет | да (по типу объекта) |
| `true` | ко всем объектам `targetObjectType` | да |
| `self.flag.value == true` | только при выполнении | да, если CEL true |

Выражение продолжается через `ExpressionEngine` в число `self` = переменные целевого объекта.

## Модели трех уровней

| Уровень | Источник | Когда в реестре |
|---------|----------|------------------|
| **System-intrinsic** | `ModelBootstrap` | Всегда; не в каталоге relative-blueprints |
| **Platform built-in** | `ModelBootstrap` | Всегда (`dashboard-v1`, `alert-rule-v1`, …) |
| **Fixtures** | `FixtureModelBootstrap` | Только `ispf.bootstrap.fixtures-enabled=true` |
| **Solution** | `bundle.json` → `models[]` | При deploy bundle |

**Внутренняя система** (схема вшивается в экземпляре, без `appliedBlueprintIds`): `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1`, `alert-rule-v1`, `correlator-v1`, `dashboard-v1`, `report-v1`, `workflow-v1`.

**Светильники** (демо/лаборатория, не часть ядра): `device-driver-v1`, `mqtt-gateway-v1`, `mqtt-sensor-v1`, `base-sensor-v1`, `vendor-sensor-ext-v1`, `snmp-agent-v1`. Конфиг: `ispf.bootstrap.fixtures-enabled` (по умолчанию `true`).

См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

## API

| Метод | Путь | Описание |
|--------|------|----------|
| GET/POST/… | `/api/v1/relative-blueprints` | RELATIVE facades |
| GET/POST/… | `/api/v1/instance-types` | INSTANCE facades (+ `?platformType=` для create dialog) |
| GET/POST/… | `/api/v1/absolute-blueprints` | ABSOLUTE facades |
| GET/POST/… | `/api/v1/blueprints` | Legacy unified API (совместимость) |

## Состав модели (`BlueprintDefinition`)

- Переменные (`ModelVariableDefinition`) — schema, default, group, readable/writable
- События (`EventDescriptor`)
- Функции (`FunctionDescriptor`)
- Binding rules (`ModelBindingRule`) — см. [BINDINGS.md](bindings.md)
- Метаданные: name, description, `ObjectType`, `BlueprintType`

## Двигатель

| Класс | Назначение |
|-------|------------|
| `ModelRegistry` | In-memory каталог моделей |
| `BlueprintEngine` | CRUD, apply, instantiate, fromObject, `applyRelativeModels` (CEL-gated) |
| `ModelBootstrap` | Platform built-in + system-intrinsic models |
| `FixtureModelBootstrap` | Demo/lab fixture models (`fixtures-enabled`) |
| `ModelPersistenceService` | Сохранение пользовательских моделей в `blueprint_definitions` (REQ-PF-07) |

Пользовательские модели (не `builtin`) управляются из БД при старте сервера после `ensureBuiltInModels()`.

## API

| Метод | Путь | Описание |
|--------|------|----------|
| GET | `/api/v1/blueprints` | Список |
| GET | `/api/v1/blueprints/{id}` | По ID |
| GET | `/api/v1/blueprints/by-name/{name}` | По имени |
| POST | `/api/v1/blueprints` | Создать |
| PUT | `/api/v1/blueprints/{id}` | Обновить |
| DELETE | `/api/v1/blueprints/{id}` | Удалить |
| POST | `/api/v1/blueprints/{id}/apply?objectPath=` | Применить к объекту |
| POST | `/api/v1/blueprints/{id}/instantiate` | Создать экземпляр |
| POST | `/api/v1/blueprints/from-object` | Экспорт модели из объекта |
| GET | `/api/v1/blueprints/attachments` | Привязки model↔type |

Доступ: **админ**. См. [API.md](api.md).

## Встроенные и крепеж-модели

### Платформа встроена (всегда)

| Модель | Тип | Назначение |
|--------|-----|------------|
| `dashboard-v1` | intrinsic RELATIVE | Layout HMI |
| `workflow-v1` | intrinsic RELATIVE | BPMN workflow |
| `report-v1` | intrinsic RELATIVE | SQL report |
| `alert-rule-v1` | intrinsic RELATIVE | CEL alert rule |
| `correlator-v1` | intrinsic RELATIVE | Event correlator |
| `data-source-v1`, `schedule-v1`, … | intrinsic RELATIVE | Схемы system object types |

### Fixtures (`ispf.bootstrap.fixtures-enabled=true`)

Демонстрационные/лабораторные модели **не** включены в ядро ​​`ModelBootstrap`. Регистрируются `FixtureModelBootstrap` при старте (если светильники включены).

#### mqtt-сенсор-v1

Демо-датчик температуры MQTT. Применяется к `demo-sensor-01` через `templateId`, а не через автоматическое применение (CEL по умолчанию пусто).

| Переменная | Группа | Описание |
|------------|--------|----------|
| `temperature` | telemetry | value, unit (history) |
| `threshold` | config | Порог |
| `alarmActive`, `alarmAcknowledged` | status | Alarm state |
| `temperaturePercent` | telemetry | Binding |

Событие: `thresholdExceeded`. Функция: `acknowledgeAlarm`.

#### mqtt-gateway-v1

Входной шлюз MQTT — один брокер, маршрутизация `lastIngress` на дочерние датчики через `dispatchTelemetry`. См. [ADR-0017](decisions/0017-telemetry-ingest-pipeline.md).

#### драйвер-v1 устройства

RELATIVE mixin с переменными группами `driver` — для демо/лаборатории и явного применения. **Не** используется для автоматического применения при создании УСТРОЙСТВА.

На производственно-пути схема водителя в ущерб при `provisionDriver()` без относительного миксина (см. [DRIVERS.md](drivers.md)).

#### snmp-агент-v1

SNMP-устройство (MIB-II + HOST-RESOURCES). Демо: `root.platform.devices.snmp-localhost`.

| Переменная | ОИД | Описание |
|------------|-----|----------|
| `sysName` | 1.3.6.1.2.1.1.5.0 | Имя хоста |
| `sysDescr` | 1.3.6.1.2.1.1.1.0 | Описание системы / ОС |
| `sysUpTime` | 1.3.6.1.2.1.1.3.0 | Uptime (TimeTicks) |
| `sysLocation` | 1.3.6.1.2.1.1.6.0 | Расположение |
| `sysContact` | 1.3.6.1.2.1.1.4.0 | Контакт |
| `hrMemorySize` | 1.3.6.1.2.1.25.2.2.0 | Объём RAM (KB) |
| `hrSystemProcesses` | 1.3.6.1.2.1.25.1.6.0 | Число процессов |
| `hrSystemNumUsers` | 1.3.6.1.2.1.25.1.5.0 | Число пользователей |
| `ifNumber` | 1.3.6.1.2.1.2.1.0 | Число сетевых интерфейсов |
| `ifInOctets` | 1.3.6.1.2.1.2.2.1.10.1 | Входящие octets (интерфейс #1) |
| `ifOutOctets` | 1.3.6.1.2.1.2.2.1.16.1 | Исходящие octets (интерфейс #1) |
| `hrProcessorLoad` | 1.3.6.1.2.1.25.3.3.1.2.1 | Загрузка CPU % (ядро #1) |

Используются дашбордом `root.platform.dashboards.snmp-host-monitoring`.

#### базовый-сенсор-v1 / вендор-сенсор-ext-v1

Расширение Семейство INSTANCE + RELATIVE для наследования моделей производителей (см. `ModelUpgradeApiTest`).

## Пример: создать экземпляр

```http
POST /api/v1/blueprints/{id}/instantiate
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "sensor-02",
  "parameters": {}
}
```

## Связанные документы

- [OBJECT_MODEL.md](object-model.md) — переменные, DataRecord
- [DASHBOARDS.md](dashboards.md) — макет Dashboard-v1.
- [WORKFLOWS.md](workflows.md) — рабочий процесс-v1
- [DRIVERS.md](drivers.md) — переменные драйвера, инициализация
- [decisions/0018-fixture-models-and-cel-applicability.md](decisions/0018-fixture-models-and-cel-applicability.md) — светильники + автоматическое применение CEL
