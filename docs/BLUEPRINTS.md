# Blueprints Plugin

Модуль `ispf-plugin-blueprint` — система **blueprint** (шаблонов структуры объектов). Один движок, три вида в дереве и API.

## Три вида моделей (`BlueprintType`)

| Тип | Каталог | Поведение |
|-----|---------|-----------|
| `RELATIVE` | `root.platform.relative-blueprints` | Optional mixins — variables/events/functions **вливаются** в существующий объект |
| `INSTANCE` | `root.platform.instance-types` | Шаблон **типа объекта** — создание экземпляров через instantiate |
| `ABSOLUTE` | `root.platform.absolute-blueprints` | Singleton blueprint — один живой объект в `root.platform.instances.*` |

**System-intrinsic schemas** (1:1 с `ObjectType`: `DATA_SOURCE`, `SCHEDULE`, `DASHBOARD`, …) хранятся в registry для bootstrap, но **не показываются** в каталоге relative-blueprints и **не попадают** в `appliedBlueprintIds`. Структура вшивается в экземпляр через `*ObjectService.ensureStructure()`.

См. [0011](decisions/0011-model-type-semantics.md).

### Связь с объектом

- `templateId` — primary model (INSTANCE/ABSOLUTE)
- `appliedBlueprintIds` — JSON-массив id всех применённых моделей (persisted)
- При merge коллизий имён: **last-wins + warning** (не ошибка)

### Auto-apply RELATIVE

При `POST /objects` с `autoApplyRelativeBlueprints=true` (default) вызывается `BlueprintEngine.applyRelativeModels()`.

**Условия auto-apply** (все обязательны):

1. `BlueprintType.RELATIVE`, не system-intrinsic
2. `targetObjectType` совпадает с типом объекта
3. **`suitabilityExpression` (Applicability condition, CEL) не пустой** и вычисляется в `true`
4. Модель ещё не в `appliedBlueprintIds`

**Пустой CEL** → модель **никогда** не применяется автоматически. Для mixin без условия используйте явный apply: `templateId` при create, `POST /api/v1/relative-blueprints/{id}/apply`, companion-models в bundle.

**Явный apply** (template / API): проверяется `targetObjectType`; CEL опционален — если задан, должен быть `true`.

См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

### Applicability condition (CEL)

Поле `suitabilityExpression` в `BlueprintDefinition` — в Web Console: *Applicability condition (CEL)*.

| Значение | Auto-apply | Явный apply |
|----------|------------|-------------|
| пусто | нет | да (по типу объекта) |
| `true` | ко всем объектам `targetObjectType` | да |
| `self.flag.value == true` | только при выполнении | да, если CEL true |

Выражение вычисляется через `ExpressionEngine` в контексте `self` = переменные целевого объекта.

## Три уровня моделей

| Уровень | Источник | Когда в registry |
|---------|----------|------------------|
| **System-intrinsic** | `ModelBootstrap` | Всегда; не в каталоге relative-blueprints |
| **Platform built-in** | `ModelBootstrap` | Всегда (`dashboard-v1`, `alert-rule-v1`, …) |
| **Fixtures** | `FixtureModelBootstrap` | Только `ispf.bootstrap.fixtures-enabled=true` |
| **Solution** | `bundle.json` → `models[]` | При deploy bundle |

**System-intrinsic** (схема вшивается в экземпляр, без `appliedBlueprintIds`): `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1`, `alert-rule-v1`, `correlator-v1`, `dashboard-v1`, `report-v1`, `workflow-v1`.

**Fixtures** (demo/lab, не часть core): `device-driver-v1`, `mqtt-gateway-v1`, `mqtt-sensor-v1`, `base-sensor-v1`, `vendor-sensor-ext-v1`, `snmp-agent-v1`. Конфиг: `ispf.bootstrap.fixtures-enabled` (default `true`).

См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

## API

| Method | Path | Описание |
|--------|------|----------|
| GET/POST/… | `/api/v1/relative-blueprints` | RELATIVE facades |
| GET/POST/… | `/api/v1/instance-types` | INSTANCE facades (+ `?platformType=` для create dialog) |
| GET/POST/… | `/api/v1/absolute-blueprints` | ABSOLUTE facades |
| GET/POST/… | `/api/v1/blueprints` | Legacy unified API (совместимость) |

## Состав модели (`BlueprintDefinition`)

- Переменные (`ModelVariableDefinition`) — schema, default, group, readable/writable
- События (`EventDescriptor`)
- Функции (`FunctionDescriptor`)
- Binding rules (`ModelBindingRule`) — см. [BINDINGS.md](BINDINGS.md)
- Метаданные: name, description, `ObjectType`, `BlueprintType`

## Engine

| Класс | Назначение |
|-------|------------|
| `ModelRegistry` | In-memory каталог моделей |
| `BlueprintEngine` | CRUD, apply, instantiate, fromObject, `applyRelativeModels` (CEL-gated) |
| `ModelBootstrap` | Platform built-in + system-intrinsic models |
| `FixtureModelBootstrap` | Demo/lab fixture models (`fixtures-enabled`) |
| `ModelPersistenceService` | Сохранение пользовательских моделей в `blueprint_definitions` (REQ-PF-07) |

Пользовательские модели (не `builtin`) восстанавливаются из БД при старте сервера после `ensureBuiltInModels()`.

## API

| Method | Path | Описание |
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

Доступ: **admin**. См. [API.md](API.md).

## Встроенные и fixture-модели

### Platform built-in (всегда)

| Модель | Тип | Назначение |
|--------|-----|------------|
| `dashboard-v1` | intrinsic RELATIVE | Layout HMI |
| `workflow-v1` | intrinsic RELATIVE | BPMN workflow |
| `report-v1` | intrinsic RELATIVE | SQL report |
| `alert-rule-v1` | intrinsic RELATIVE | CEL alert rule |
| `correlator-v1` | intrinsic RELATIVE | Event correlator |
| `data-source-v1`, `schedule-v1`, … | intrinsic RELATIVE | Схемы system object types |

### Fixtures (`ispf.bootstrap.fixtures-enabled=true`)

Demo/lab модели **не** входят в core `ModelBootstrap`. Регистрируются `FixtureModelBootstrap` при старте (если fixtures включены).

#### mqtt-sensor-v1

Demo MQTT temperature sensor. Применяется к `demo-sensor-01` через `templateId`, не через auto-apply (CEL по умолчанию пуст).

| Переменная | Группа | Описание |
|------------|--------|----------|
| `temperature` | telemetry | value, unit (history) |
| `threshold` | config | Порог |
| `alarmActive`, `alarmAcknowledged` | status | Alarm state |
| `temperaturePercent` | telemetry | Binding |

Событие: `thresholdExceeded`. Функция: `acknowledgeAlarm`.

#### mqtt-gateway-v1

MQTT ingress gateway — один broker, маршрутизация `lastIngress` на child sensors через `dispatchTelemetry`. См. [ADR-0017](decisions/0017-telemetry-ingest-pipeline.md).

#### device-driver-v1

RELATIVE mixin с переменными группы `driver` — для demo/lab и явного apply. **Не** используется для auto-apply при create DEVICE.

На production-пути схема драйвера встраивается при `provisionDriver()` без relative mixin (см. [DRIVERS.md](DRIVERS.md)).

#### snmp-agent-v1

SNMP-устройство (MIB-II + HOST-RESOURCES). Демо: `root.platform.devices.snmp-localhost`.

| Переменная | OID | Описание |
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

#### base-sensor-v1 / vendor-sensor-ext-v1

Семейство INSTANCE + RELATIVE extension для демонстрации model inheritance (см. `ModelUpgradeApiTest`).

## Пример: instantiate

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

- [OBJECT_MODEL.md](OBJECT_MODEL.md) — переменные, DataRecord
- [DASHBOARDS.md](DASHBOARDS.md) — dashboard-v1 layout
- [WORKFLOWS.md](WORKFLOWS.md) — workflow-v1
- [DRIVERS.md](DRIVERS.md) — driver variables, provisioning
- [decisions/0018-fixture-models-and-cel-applicability.md](decisions/0018-fixture-models-and-cel-applicability.md) — fixtures + CEL auto-apply
