> **Язык:** русская версия. Канонический английский: [en/decisions/0011-model-type-semantics.md](../../en/decisions/0011-model-type-semantics.md).

# ADR-0011: Blueprint + три вида (MIXIN / INSTANCE / SINGLETON)

> **Поправка (2026-10-11):** терминология и каталоги — **MIXIN / INSTANCE / SINGLETON** (`BlueprintType`, переименование V88: RELATIVE→MIXIN и ABSOLUTE→SINGLETON). Каталоги: `mixin-blueprints`, `instance-types`, `singleton-blueprints`. Старые формулировки RELATIVE/ABSOLUTE — только история.

## Статус

Accepted (2026-06-23) — терминология уточнена 2026-10-11

## Контекст

Виды blueprint были реализованы неравномерно под старыми именами (`RELATIVE` / `INSTANCE` / `ABSOLUTE`): один `templateId` на объект, in-memory attachments, неполный auto-apply и singleton-хабы без ясной runtime-семантики. Смешение видов в одном каталоге «Models» и одном API путало людей и агентов.

## Решение

### 1. Один Blueprint, три вида снаружи

- **Внутри:** общий `BlueprintDefinition` + `BlueprintEngine` + таблица `blueprint_definitions`.
- **Снаружи:** три каталога в дереве:
  - `root.platform.mixin-blueprints` — mixin (MIXIN)
  - `root.platform.instance-types` — шаблоны экземпляров (INSTANCE)
  - `root.platform.singleton-blueprints` — singleton live-хабы (SINGLETON)
- Три API-фасада: `/api/v1/mixin-blueprints`, `/instance-types`, `/singleton-blueprints` (общий registry, разная валидация и операции).
- `root.platform.models` удалён при старте; узлы мигрируют в типизированные каталоги.

### 2. Связь объект ↔ blueprints

| Поле | Назначение |
|------|------------|
| `templateId` | Основной blueprint INSTANCE (или legacy) |
| `appliedBlueprintIds` | Упорядоченный JSON-массив id всех применённых blueprint |

`ModelAttachment` восстанавливается из `appliedBlueprintIds` при старте (только metadata, без re-merge).

### 3. Применимость MIXIN

- `targetObjectType` + CEL `suitabilityExpression` (*Applicability condition* в UI).
- **Auto-apply** при create (`applyRelativeModels` / mixin auto-apply, флаг `autoApplyRelativeBlueprints`, по умолчанию true): применяется **только** если CEL **не пустой** и вычисляется в `true`. Пустой CEL → auto-apply **не выполняется**.
- **System-intrinsic** MIXIN (`parameters.systemIntrinsic=true`, например `data-source-v1`) **исключены** из auto-apply и каталога — схема вшивается в экземпляр через `SystemObjectStructureService`.
- **Явный apply** (`templateId`, API `/mixin-blueprints/{id}/apply`, companion-blueprints): CEL необязателен; проверяется `targetObjectType`.
- **Fixture blueprints** (`mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, …) — не core built-in; см. [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md).
- Схема переменных драйвера на `DEVICE` при provisioning — встроенная структура, не mixin auto-apply; см. [0018](0018-fixture-models-and-cel-applicability.md).
- Opt-in reevaluation и ownership-aware detach: [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md).

### 4. INSTANCE как виртуальный тип

- UI показывает имя blueprint INSTANCE как «тип»; в БД: `type = targetObjectType`, `templateId = blueprint.id`.
- Enum `ObjectType` не расширяется под пользовательские blueprint.

### 5. SINGLETON

- При создании — ровно один live-объект по `parameters.singletonInstancePath` (по умолчанию `root.platform.singleton-blueprints.{name}`).
- Этот узел каталога **и есть** runtime-объект с логикой приложения (переменные, события, функции, binding rules) — не зеркало под `root.platform.instances`.
- `PUT` blueprint синхронизирует структуру в singleton (merge, runtime-значения сохраняются).
- Повторный instantiate → 409.

### 6. Конфликты merge: warn + last-wins

Когда имена variable/event/function сталкиваются при apply нескольких blueprint:

- **last-wins** — apply не откатывается, HTTP 200.
- **warnings** в результате apply — пользователь видит конфликт и решает сам.

### 7. Binding rules

`ModelApplicationService.applyModelWithRules()` — единая точка входа: merge структуры + `ModelBindingRulesMerger` на всех путях (create, template, instantiate, driver, mixin auto-apply).

## Последствия

- Flyway `V39__object_applied_models.sql`; переименование каталогов `V88__rename_mixin_singleton_blueprints.sql`.
- `PlatformObject.appliedBlueprintIds`, расширенный `ObjectDto`.
- UI: три каталога, диалог создания с типами INSTANCE, инспектор со списком применённых blueprint.
- Документация: [blueprints](../blueprints.md), [object-model](../object-model.md).

## Связанные

- [blueprints](../blueprints.md)
- [bindings](../bindings.md)
- [0010-binding-rules-only](0010-binding-rules-only.md)
- [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md)
- [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md)
