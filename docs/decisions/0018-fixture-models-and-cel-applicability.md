# ADR-0018: Fixture-модели и CEL applicability для RELATIVE

Статус: **Accepted**  
Дата: 2026-06-25

## Контекст

Три пересекающиеся проблемы:

1. **Demo/lab модели в core** — `mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, `snmp-agent-v1` и семейство `base-sensor-v1` регистрировались как встроенные модели платформы и попадали в auto-apply при создании любого `DEVICE`.
2. **`device-driver-v1` как relative mixin** — при `autoApplyRelativeModels=true` схема драйвера вливалась до `provisionDriver(mqtt)` с дефолтом `driverId=virtual`, после чего смена драйвера отклонялась runtime.
3. **Пустой CEL = «подходит всем»** — `suitabilityExpression` (в UI: *Applicability condition*) при пустом значении трактовался как unconditional match по `targetObjectType`, что делало auto-apply непредсказуемым.

Стандартные вещи платформы (dashboard, workflow, data-source schema) не должны быть optional relative mixins. Demo- и lab-шаблоны не должны жить в core registry без явного включения.

## Решение

### 1. Три уровня моделей

| Уровень | Регистрация | Каталог relative-models | Auto-apply RELATIVE |
|---------|-------------|-------------------------|---------------------|
| **System-intrinsic** | `ModelBootstrap` + `parameters.systemIntrinsic=true` | Нет | Нет — структура вшивается через `SystemObjectStructureService` |
| **Platform built-in** | `ModelBootstrap.ensureBuiltInModels()` | Да (если не intrinsic) | Только при непустом CEL (см. §2) |
| **Fixtures** | `FixtureModelBootstrap` при `ispf.bootstrap.fixtures-enabled=true` | Да | Только при непустом CEL; иначе — явный `templateId` / API apply |

**System-intrinsic** (всегда): `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1`, `alert-rule-v1`, `correlator-v1`, `dashboard-v1`, `report-v1`, `workflow-v1`.

**Fixtures** (опционально, default `fixtures-enabled=true`):

| Модель | Назначение |
|--------|------------|
| `device-driver-v1` | RELATIVE mixin: переменные группы `driver` (demo/lab) |
| `mqtt-gateway-v1` | MQTT ingress gateway + `dispatchTelemetry` |
| `mqtt-sensor-v1` | Demo MQTT temperature sensor |
| `base-sensor-v1` | Blueprint семейства датчиков (INSTANCE) |
| `vendor-sensor-ext-v1` | Расширение base-sensor (RELATIVE) |
| `snmp-agent-v1` | SNMP demo device |

Решения (solution bundles) поставляют свои модели через `models[]` в `bundle.json` (см. `examples/lab-mqtt-temperature/`).

### 2. CEL applicability (`suitabilityExpression`)

Поле `ModelDefinition.suitabilityExpression` — **Applicability condition (CEL)** в редакторе моделей.

| Путь применения | Пустой CEL | Непустой CEL |
|-----------------|------------|--------------|
| **Auto-apply** (`applyRelativeModels` при `POST /objects`, `autoApplyRelativeModels=true`) | **Не применяется** | Применяется, если CEL → `true` и `targetObjectType` совпадает |
| **Явный apply** (`templateId`, `POST /relative-models/{id}/apply`, companion-models) | Разрешён (проверяется только `targetObjectType`) | CEL должен вычислиться в `true` |

Реализация: `ModelEngine.isSuitableForAutoApply()` — пустой CEL → `false`; `assertSuitable()` для ручного apply — CEL опционален.

Примеры CEL для auto-apply:

```cel
true
self.templateId == "mqtt-sensor-v1"
self.driverId.value == "mqtt"
```

### 3. Схема драйвера на DEVICE (не relative auto-apply)

Переменные `driverId`, `driverStatus`, `driverConfigJson`, … на **любом** `DEVICE` при provisioning встраиваются через `DeviceProvisioningService` → `SystemObjectStructureService.ensureDeviceDriverStructure()` из blueprint `FixtureModelDefinitions.buildDeviceDriverModel()` (**без** записи в каталог и **без** `appliedModelIds`).

Это отдельно от RELATIVE mixin `device-driver-v1` (fixture для demo/lab и явного apply).

Порядок при `POST /objects` (DEVICE + `driverId`):

1. Создание узла
2. `applyTemplate(templateId)` — если задан
3. `applyRelativeModelsWithRules` — только модели с непустым CEL
4. `provisionDriver(driverId)` — встраивание driver schema + configure runtime

### 4. Конфигурация fixtures

```yaml
ispf:
  bootstrap:
    fixtures-enabled: ${ISPF_BOOTSTRAP_FIXTURES_ENABLED:true}
```

При `fixtures-enabled=false`: demo-узлы в `PlatformBootstrap.initializeFixtures()` не создаются; fixture-модели не регистрируются; driver provisioning на DEVICE по-прежнему работает (embedded schema).

## Последствия

- `device-driver-v1`, `mqtt-gateway-v1` удалены из `ModelBootstrap` и `SystemIntrinsicModels`.
- Код: `FixtureModelBootstrap`, `FixtureModelDefinitions`, обновлённый `ModelEngine`.
- Существующие RELATIVE-модели без CEL перестают auto-apply’иться — для auto-apply нужно задать выражение в редакторе.
- Документация: [MODELS.md](../MODELS.md), [DRIVERS.md](../DRIVERS.md).

## Связанные материалы

- [ADR-0011](0011-model-type-semantics.md) — три вида моделей
- [ADR-0017](0017-telemetry-ingest-pipeline.md) — `mqtt-gateway-v1` ingest
- [MODELS.md](../MODELS.md)
- [DRIVERS.md](../DRIVERS.md)
