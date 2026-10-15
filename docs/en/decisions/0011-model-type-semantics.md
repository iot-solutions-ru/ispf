# ADR-0011: Blueprint + three kinds (MIXIN / INSTANCE / SINGLETON)

> **Amendment (2026-10-11):** Terminology and catalogs are **MIXIN / INSTANCE / SINGLETON** (`BlueprintType`, V88 rename from RELATIVE→MIXIN and ABSOLUTE→SINGLETON). Catalogs: `mixin-blueprints`, `instance-types`, `singleton-blueprints`. Older RELATIVE/ABSOLUTE wording is historical only.

## Status

Accepted (2026-06-23) — terminology amended 2026-10-11

## Context

Blueprint kinds were implemented unevenly under older names (`RELATIVE` / `INSTANCE` / `ABSOLUTE`): one `templateId` per object, in-memory attachments, incomplete auto-apply, and singleton hubs without clear runtime semantics. Mixing kinds in one "Models" catalog and one API caused confusion for humans and agents.

## Decision

### 1. One Blueprint, three kinds externally

- **Internally:** shared `BlueprintDefinition` + `BlueprintEngine` + table `blueprint_definitions`.
- **Externally:** three catalogs in the tree:
  - `root.platform.mixin-blueprints` — mixin (MIXIN)
  - `root.platform.instance-types` — instance templates (INSTANCE)
  - `root.platform.singleton-blueprints` — singleton live hubs (SINGLETON)
- Three API facades: `/api/v1/mixin-blueprints`, `/instance-types`, `/singleton-blueprints` (shared registry, different validation and operations).
- `root.platform.models` removed at startup; nodes migrate to typed catalogs.

### 2. Object ↔ blueprints link

| Field | Purpose |
|-------|---------|
| `templateId` | Primary INSTANCE (or legacy) blueprint id |
| `appliedBlueprintIds` | Ordered JSON array of all applied blueprint ids |

`ModelAttachment` is restored from `appliedBlueprintIds` at startup (metadata only, no re-merge).

### 3. MIXIN applicability

- `targetObjectType` + CEL `suitabilityExpression` (*Applicability condition* in UI).
- **Auto-apply** on create (`applyRelativeModels` / mixin auto-apply, flag `autoApplyRelativeBlueprints`, default true): applies **only** if CEL is **non-empty** and evaluates to `true`. Empty CEL → auto-apply **does not run**.
- **System-intrinsic** MIXIN (`parameters.systemIntrinsic=true`, e.g. `data-source-v1`) **excluded** from auto-apply and catalog — schema embedded in instance via `SystemObjectStructureService`.
- **Explicit apply** (`templateId`, API `/mixin-blueprints/{id}/apply`, companion-blueprints): CEL optional; `targetObjectType` is checked.
- **Fixture blueprints** (`mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, …) — not core built-in; see [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md).
- Driver variable schema on `DEVICE` at provisioning — embedded structure, not mixin auto-apply; see [0018](0018-fixture-models-and-cel-applicability.md).
- Opt-in reevaluation and ownership-aware detach: [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md).

### 4. INSTANCE as virtual type

- UI shows INSTANCE blueprint name as "type"; in DB: `type = targetObjectType`, `templateId = blueprint.id`.
- Enum `ObjectType` is not extended for user-defined blueprints.

### 5. SINGLETON

- On create — exactly one live object at `parameters.singletonInstancePath` (default `root.platform.singleton-blueprints.{name}`).
- That catalog node **is** the runtime object with application logic (variables, events, functions, binding rules) — not a mirror under `root.platform.instances`.
- `PUT` on blueprint syncs structure to the singleton (merge, runtime values preserved).
- Repeat instantiate → 409.

### 6. Merge conflicts: warn + last-wins

When variable/event/function names collide on apply of multiple blueprints:

- **last-wins** — apply does not roll back, HTTP 200.
- **warnings** in apply result — user sees conflict and decides.

### 7. Binding rules

`ModelApplicationService.applyModelWithRules()` — single entry point: merge structure + `ModelBindingRulesMerger` on all paths (create, template, instantiate, driver, mixin auto-apply).

## Consequences

- Flyway `V39__object_applied_models.sql`; catalog rename `V88__rename_mixin_singleton_blueprints.sql`.
- `PlatformObject.appliedBlueprintIds`, extended `ObjectDto`.
- UI: three catalogs, create dialog with INSTANCE types, inspector with applied blueprints list.
- Documentation: [blueprints](../blueprints.md), [object-model](../object-model.md).

## Related

- [blueprints](../blueprints.md)
- [bindings](../bindings.md)
- [0010-binding-rules-only](0010-binding-rules-only.md)
- [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md)
- [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md)
