# ADR-0011: Blueprint + three model kinds

> **Note (2026):** domain renamed Model → Blueprint (`ObjectType.BLUEPRINT`, `ispf-plugin-blueprint`, `/api/v1/blueprints`, catalogs `relative-blueprints` / `absolute-blueprints`). Term "Instance Types" unchanged. Original ADR wording preserved below.

## Status

Accepted (2026-06-23)

## Context

Types `RELATIVE`, `INSTANCE`, `ABSOLUTE` are declared in `BlueprintType`, but semantics were implemented unevenly: one `templateId` per object, in-memory attachments, `applyRelativeModels()` not called, `ABSOLUTE` without logic. Mixing them in one "Models" catalog and one API caused confusion.

## Decision

### 1. One Blueprint, three kinds externally

- **Internally:** shared `BlueprintDefinition` + `BlueprintEngine` + table `blueprint_definitions`.
- **Externally:** three catalogs in the tree:
  - `root.platform.relative-blueprints` — mixin (RELATIVE)
  - `root.platform.instance-types` — instance templates (INSTANCE)
  - `root.platform.absolute-blueprints` — singleton (ABSOLUTE)
- Three API facades: `/api/v1/relative-blueprints`, `/instance-types`, `/absolute-models` (shared registry, different validation and operations).
- `root.platform.models` removed at startup; nodes migrate to typed catalogs.

### 2. Object ↔ models link

| Field | Purpose |
|-------|---------|
| `templateId` | Primary model (INSTANCE/ABSOLUTE); backward compat |
| `appliedBlueprintIds` | Ordered JSON array of all applied model ids |

`ModelAttachment` is restored from `appliedBlueprintIds` at startup (metadata only, no re-merge).

### 3. RELATIVE applicability

- `targetObjectType` + CEL `suitabilityExpression` (*Applicability condition* in UI).
- **Auto-apply** on create (`applyRelativeModels`, flag `autoApplyRelativeBlueprints`, default true): model applies **only** if CEL is **non-empty** and evaluates to `true`. Empty CEL → auto-apply **does not run**.
- **System-intrinsic** RELATIVE (`parameters.systemIntrinsic=true`, e.g. `data-source-v1`) **excluded** from auto-apply and catalog — schema embedded in instance via `SystemObjectStructureService`.
- **Explicit apply** (`templateId`, API `/relative-blueprints/{id}/apply`, companion-blueprints): CEL optional; `targetObjectType` is checked.
- **Fixture models** (`mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, …) — not core built-in; see [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md).
- Driver variable schema on `DEVICE` at provisioning — embedded structure, not relative auto-apply; see [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md).

### 4. INSTANCE as virtual type

- UI shows INSTANCE model name as "type"; in DB: `type = targetObjectType`, `templateId = model.id`.
- Enum `ObjectType` is not extended for user-defined models.

### 5. ABSOLUTE singleton

- On model create — exactly one object at `parameters.absoluteInstancePath` (default `root.platform.instances.{name}`).
- `PUT` on model syncs structure to singleton (merge, runtime values preserved).
- Repeat instantiate → 409.

### 6. Merge conflicts: warn + last-wins

When variable/event/function names collide on apply of multiple models:

- **last-wins** — apply does not roll back, HTTP 200.
- **warnings** in `ModelApplyResult` — user sees conflict and decides.

### 7. Binding rules

`ModelApplicationService.applyModelWithRules()` — single entry point: merge structure + `ModelBindingRulesMerger` on all paths (create, template, instantiate, driver, relative auto-apply).

## Consequences

- Flyway `V39__object_applied_models.sql`.
- `PlatformObject.appliedBlueprintIds`, extended `ObjectDto`.
- UI: three catalogs, create dialog with INSTANCE types, inspector with applied models list.
- Documentation: [blueprints](../blueprints.md), [object-model](../object-model.md).

## Related

- [blueprints](../blueprints.md)
- [bindings](../bindings.md)
- [0010-binding-rules-only](0010-binding-rules-only.md) (binding rules only)
- [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md) (fixtures + CEL auto-apply)
