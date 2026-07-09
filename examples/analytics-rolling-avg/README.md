# Historian rolling average (ADR-0041)

Walkthrough: **historian binding rule** on a device → live derived variable → dashboard chart.

> **Supersedes** the BL-160 `ANALYTICS_TEMPLATE` flow. Legacy template docs: [reference-asset-analytics.md](../../docs/en/reference-asset-analytics.md).

Full recipes (OEE, tag chains, CEL): [analytics-historian-cookbook.md](../../docs/en/analytics-historian-cookbook.md)

## Prerequisites

- ISPF server with historian enabled (`temperature` or your source tag has `historyEnabled=true`).
- Demo device `root.platform.devices.demo-sensor-01` with historian samples.

## 1. Add a historian rule

**Web console:** Explorer → device → **Computations** → preset **Rolling average (5m)** or **+ Rule** with `kind: historian`.

**REST:** merge into `@bindingRules`:

```http
PUT /api/v1/objects/by-path/binding-rules?path=root.platform.devices.demo-sensor-01
```

```json
[
  {
    "id": "avg-temp-5m",
    "name": "Rolling average",
    "enabled": true,
    "order": 10,
    "kind": "historian",
    "activators": {
      "onStartup": false,
      "onVariableChange": [
        { "objectPath": "root.platform.devices.demo-sensor-01", "variableName": "temperature" }
      ],
      "onEvent": null,
      "periodicMs": 60000
    },
    "condition": "",
    "expression": "rollingAvg(root.platform.devices.demo-sensor-01.temperature, 5m)",
    "windowBucket": "5m",
    "target": { "kind": "variable", "variableName": "avgTemp5m", "field": "value" }
  }
]
```

Target variable `avgTemp5m` is created automatically if missing.

## 2. Verify catalog

```http
GET /api/v1/platform/analytics/tags/by-path?path=root.platform.devices.demo-sensor-01#avg-temp-5m
```

Expect `helper: rollingAvg`, `outputVariable: avgTemp5m`, lineage sources pointing at `temperature`.

## 3. Chart widget

On a dashboard **chart** or **value** widget:

- **Object path:** `root.platform.devices.demo-sensor-01`
- **Variable:** `avgTemp5m` (not `derivedValue`)

For multi-tag history charts use analytics query mode — see cookbook § Dashboard binding.

## 4. Tag chain (optional)

To build a three-level KPI pipeline (sensor → smooth → smooth again), add rules on separate devices feeding each other's output variables. See cookbook **Recipe 3**.

## REST API (current)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` / `PUT` | `/api/v1/objects/by-path/binding-rules?path=…` | List / save rules (reactive + historian) |
| `GET` | `/api/v1/platform/analytics/tags?path=…` | Tag catalog |
| `GET` | `/api/v1/platform/analytics/tags/by-path?path=…` | One tag (`objectPath#ruleId`) |
| `POST` | `/api/v1/platform/analytics/expression/validate` | CEL + `hist.*` validation |

Deprecated for new work: `/api/v1/platform/analytics/templates/*`, `derived-tags/refresh` as primary workflow.

## Related

- [docs/en/analytics-tag-catalog.md](../../docs/en/analytics-tag-catalog.md)
- [docs/en/decisions/0041-multi-tag-historian-computations.md](../../docs/en/decisions/0041-multi-tag-historian-computations.md)
