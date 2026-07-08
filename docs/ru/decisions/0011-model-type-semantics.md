> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0011-model-type-semantics.md](../../en/decisions/0011-model-type-semantics.md).

# ADR-0011: Blueprint + три вида моделей

> **Примечание (2026):** домен переименован Model → Blueprint (`ObjectType.BLUEPRINT`, `ispf-plugin-blueprint`, `/api/v1/blueprints`, каталоги `relative-blueprints` / `absolute-blueprints`). Термин «Instance Types» без изменений. Ниже сохранена исходная формулировка ADR.

Статус: **Принято**  
Дата: 2026-06-23

## Контекст

Типы `RELATIVE`, `INSTANCE`, `ABSOLUTE` объявлены в `BlueprintType`, но семантика реализована неравномерно: один `templateId` на объект, in-memory attachments, `applyRelativeModels()` не вызывается, `ABSOLUTE` без логики. Смешение в одном каталоге «Models» и одном API создаёт путаницу.

## Решение

### 1. Один Blueprint, три вида снаружи

- **Внутри:** общий `BlueprintDefinition` + `BlueprintEngine` + таблица `blueprint_definitions`.
- **Снаружи:** три каталога в дереве:
  - `root.platform.relative-blueprints` — mixin (RELATIVE)
  - `root.platform.instance-types` — шаблоны экземпляров (INSTANCE)
  - `root.platform.absolute-blueprints` — singleton (ABSOLUTE)
- Три API-фасада: `/api/v1/relative-blueprints`, `/instance-types`, `/absolute-models` (общий registry, разная валидация и операции).
- `root.platform.models` удалён при старте; узлы мигрируют в типизированные каталоги.

### 2. Связь объект ↔ модели

| Поле | Назначение |
|------|------------|
| `templateId` | Primary model (INSTANCE/ABSOLUTE); backward compat |
| `appliedBlueprintIds` | Упорядоченный JSON-массив id всех применённых моделей |

`ModelAttachment` восстанавливается из `appliedBlueprintIds` при старте (metadata only, без re-merge).

### 3. RELATIVE applicability

- `targetObjectType` + CEL `suitabilityExpression` (*Applicability condition* в UI).
- **Auto-apply** при create (`applyRelativeModels`, флаг `autoApplyRelativeBlueprints`, default true): модель применяется **только** если CEL **не пустой** и вычисляется в `true`. Пустой CEL → auto-apply **не выполняется**.
- **System-intrinsic** RELATIVE (`parameters.systemIntrinsic=true`, напр. `data-source-v1`) **исключены** из auto-apply и каталога — схема вшивается в экземпляр через `SystemObjectStructureService`.
- **Явный apply** (`templateId`, API `/relative-blueprints/{id}/apply`, companion-blueprints): CEL опционален; проверяется `targetObjectType`.
- **Fixture-модели** (`mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, …) — не core built-in; см. [ADR-0018](0018-fixture-models-and-cel-applicability.md).
- Схема driver-переменных на `DEVICE` при provisioning — embedded structure, не relative auto-apply; см. [ADR-0018](0018-fixture-models-and-cel-applicability.md).

### 4. INSTANCE как виртуальный тип

- UI показывает имя INSTANCE-модели как «тип»; в БД: `type = targetObjectType`, `templateId = model.id`.
- Enum `ObjectType` не расширяется под пользовательские модели.

### 5. ABSOLUTE singleton

- При создании модели — ровно один объект по `parameters.absoluteInstancePath` (default `root.platform.instances.{name}`).
- `PUT` модели синхронизирует структуру в singleton (merge, runtime values сохраняются).
- Повторный instantiate → 409.

### 6. Merge conflicts: warn + last-wins

При коллизии имён variables/events/functions при apply нескольких моделей:

- **last-wins** — apply не откатывается, HTTP 200.
- **warnings** в `ModelApplyResult` — пользователь видит конфликт и решает сам.

### 7. Binding rules

`ModelApplicationService.applyModelWithRules()` — единая точка: merge structure + `ModelBindingRulesMerger` во всех путях (create, template, instantiate, driver, relative auto-apply).

## Последствия

- Flyway `V39__object_applied_models.sql`.
- `PlatformObject.appliedBlueprintIds`, расширенный `ObjectDto`.
- UI: три каталога, create dialog с INSTANCE-типами, инспектор со списком применённых моделей.
- Документация: [blueprints.md](../BLUEPRINTS.md), [object-model.md](../object-model.md).

## Связанные материалы

- [blueprints.md](../BLUEPRINTS.md)
- [bindings.md](../BINDINGS.md)
- [ADR-0010](0010-binding-rules-only.md) (binding rules only)
- [ADR-0018](0018-fixture-models-and-cel-applicability.md) (fixtures + CEL auto-apply)
