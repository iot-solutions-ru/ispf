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
  "expression": "rollingAvg(root.platform.devices.sensor-a.temperature, 5m)",
  "windowBucket": "5m",
  "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
}
```

- **Multiple rules per device** — arbitrary `target.variableName`
- **Tag identity** — `tagPath = objectPath#ruleId` for DAG, catalog, schedules
- **Reactive engine** skips `kind=historian`; **analytics engine** compiles and evaluates them

Expression forms: builtins (`rollingAvg`, `rateOfChange`, `oee`), CEL with `hist.*` (BL-211). See [analytics-historian-cookbook.md](../analytics-historian-cookbook.md).

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

- [ADR-0040](0040-unified-computations-ui.md)
- [ADR-0010](0010-binding-rules-only.md)
- [ADR-0038](0038-analytics-platform-architecture.md) — amended §2
- [analytics-historian-cookbook.md](../analytics-historian-cookbook.md)
- [analytics-tag-catalog.md](../analytics-tag-catalog.md)
