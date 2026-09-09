# ADR-0058: MIXIN reevaluation and ownership-aware detach

## Status

Accepted (2026-09-09)

## Context

ADR-0018 made MIXIN auto-apply predictable: non-empty CEL on create/instantiate only; empty CEL never auto-applies. There is no unapply path, no contribution ownership, and no re-scan on server start or later object appearance.

Product need: optional watch mixins that re-check suitability on selected triggers and **remove their contributions** when CEL becomes false — without scanning every MIXIN on every tree event.

## Decision

### 1. Opt-in reevaluation

MIXIN may declare reevaluation config (API field `reevaluation`; persisted in blueprint `parameters`):

| Field | Meaning |
|-------|---------|
| `enabled` | When false/absent — ADR-0018 only (no watch, no auto-detach) |
| `triggers` | v1: `OBJECT_CREATED`, `SERVER_READY` |

Auto paths still require non-empty `suitabilityExpression` (ADR-0018). Explicit apply unchanged.

### 2. Attach / detach on each reevaluation pass

For watch mixins only:

| CEL | Already applied? | Action |
|-----|------------------|--------|
| true | no | **attach** (merge + record ownership + `appliedBlueprintIds`) |
| true | yes | noop |
| false | yes | **detach** (remove owned contributions + drop id) |
| false | no | noop |

### 3. Ownership-aware detach (1B)

Each object persists `blueprintContributions`: map `blueprintId → { variables, events, functions, bindingRuleIds }`.

- **Attach** claims contributed names for this id (last-apply wins: name removed from previous owner’s list).
- **Detach** hard-deletes a name only if this id is still the owner; otherwise skip + warn.
- Missing manifest (legacy apply): fall back to model-declared names; delete a variable only if no other contribution claims it.
- Do not detach INSTANCE `templateId` structure, system-intrinsic embeds, or driver schema from `ensureDeviceDriverStructure`.
- Historian purge is out of scope for v1.

### 4. Runtime

- `WatchMixinIndex` — only `reevaluation.enabled` MIXINs, keyed by trigger.
- `MixinReevaluationService` — single attach/detach evaluator.
- `ObjectChangeAsyncHandler` on `CREATED` for `OBJECT_CREATED` watchers.
- After tree + attachment restore — `SERVER_READY` pass (candidates by `targetObjectType` only).
- Explicit APIs: detach, reevaluate (mixin or object).

### 5. Out of scope (v2)

`VARIABLE_UPDATED`, `OBJECT_EVENT` triggers; soft-detach; auto-detach for non-watch mixins; historian purge.

## Consequences

- Sticky mixins (no reevaluation) remain sticky until explicit detach.
- Watch mixins can drop telemetry variables they still own when CEL fails — intentional.
- Contribution column on `object_nodes` required for durable ownership.

## Related

- [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md)
- [0011-model-type-semantics](0011-model-type-semantics.md)
- [blueprints](../blueprints.md)
