# ADR-0041: Multi-tag historian computations (binding rules)

## Status

**Accepted** (2026-07-09)

## Context

ADR-0040 unified the **Computations** UI but left historian tags on a separate MVP model: one `derivedValue`/`oeePct` per device, `ANALYTICS_TEMPLATE` objects under `root.platform.analytics`, and device-level metadata variables.

Pre-1.0: no production legacy. The MVP model confused operators («template in tree» vs «live tag on device»).

## Decision

### 1. Historian computations = `BindingRule` with `kind: historian`

Stored in `@bindingRules` alongside reactive rules:

```json
{
  "id": "avg-temp-5m",
  "kind": "historian",
  "enabled": true,
  "activators": {
    "periodicMs": 60000,
    "onVariableChange": [{ "objectPath": "root.platform.devices.sensor-a", "variableName": "temperature" }]
  },
  "expression": "avg(root.platform.devices.sensor-a/temperature, 5m)",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
}
```

- **Multiple rules per device** — arbitrary `target.variableName`
- **Tag identity** — `tagPath = objectPath#ruleId` for DAG, catalog, schedules
- **Reactive engine** skips `kind=historian`; **analytics engine** compiles and evaluates them

Expression forms: builtins (`rollingAvg`, `rateOfChange`, `oee`), CEL with `hist.*` (BL-211). See [analytics-historian-cookbook](../analytics-historian-cookbook.md).

### 2. Remove `ANALYTICS_TEMPLATE` tree catalog

- No bootstrap of `root.platform.analytics.*` template objects
- **Presets** are static server recipes (`HistorianComputationPresets`, cookbook) — not toolbar buttons in the object inspector
- `/api/v1/platform/analytics/templates/*` deprecated; new work uses binding rules API

### 3. Per-rule metadata

`@historianRuleMeta` JSON on device — `quality`, `lastEvalAt`, `lastEvalStatus` keyed by rule id.

### 4. Deprecate

- `derivedValue` / `oeePct` as sole tag detectors
- `analytics-tag-v1` metadata vars (`analyticsExpression`, `analyticsHelper`, …) as source of truth
- `applyTemplateToDevice` workflow for new configurations
- Parallel `AnalyticsDerivedTagService` refresh as primary path (engine evaluates historian rules)

## Consequences

- Operators configure all computations on one tab, one rule list
- Charts/widgets reference object variables directly (output variable names from rules)
- DAG edges follow `(objectPath, outputVariable)` producers — supports **tag chains** and **OEE** on the same device as other KPIs
- Cookbook documents OEE, three-level chains, and cross-device CEL composites

## Related

- [0040-unified-computations-ui](0040-unified-computations-ui.md)
- [analytics-historian-cookbook](../analytics-historian-cookbook.md) — Recipe 5 (`analytics-demo` on prod)
- [analytics-tag-catalog](../analytics-tag-catalog.md)

### Implementation status (2026-07-09)

| ADR decision | Status |
|--------------|--------|
| `kind: historian` in `@bindingRules` | Done |
| Catalog `objectPath#ruleId`, DAG / lineage | Done |
| `@historianRuleMeta` per rule id | Done |
| Unified Computations tab (ADR-0040) | Done |
| No `ANALYTICS_TEMPLATE` bootstrap | Done |
| Presets in code + editor catalog (no toolbar) | Done |
| Prod reference + dashboard + deploy/tools scripts | Done |
| `/templates/*` API | Deprecated for new configs |

Full checklist: [cookbook § Implementation checklist](../analytics-historian-cookbook.md#implementation-checklist-adr-0040--adr-0041).
