# Historian rolling average (ADR-0041)

Walkthrough: **historian binding rule** on a device → live derived variable → dashboard chart.

> **Supersedes** the BL-160 `ANALYTICS_TEMPLATE` flow. Legacy template docs: [reference-asset-analytics.md](../../docs/en/reference-asset-analytics.md).

Full recipes (OEE, tag chains, CEL): [analytics-historian-cookbook.md](../../docs/en/analytics-historian-cookbook.md)

**Production reference (3-tag chain + dashboard):** [cookbook Recipe 5](../../docs/en/analytics-historian-cookbook.md#recipe-5--full-production-example-analytics-demo)

```powershell
python deploy/tools/setup-historian-chain-example.py https://ispf.iot-solutions.ru
python deploy/tools/setup-historian-chain-dashboard.py https://ispf.iot-solutions.ru
```

## Prerequisites

- ISPF server with historian enabled (`temperature` or your source tag has `historyEnabled=true`).
- Demo device with historian samples — e.g. lab `demo-sensor-01` or prod `analytics-demo.sensor-a` (virtual driver).

## 1. Add a historian rule

**Web console:** Explorer → device → **Computations** → **+ Rule** → type **Historian** → expression in modal editor (use catalog for `rollingAvg` / `hist.avg`).

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

Target variable `avgTemp5m` is created automatically if missing. Enable `historyEnabled` on source and output variables for charts.

## 2. Verify catalog

```http
GET /api/v1/platform/analytics/tags/by-path?path=root.platform.devices.demo-sensor-01#avg-temp-5m
```

Expect `helper: rollingAvg`, `outputVariable: avgTemp5m`, lineage sources pointing at `temperature`.

Check rule metadata (diagnostics only — **not** an expression source):

```http
GET /api/v1/objects/by-path/variables?path=root.platform.devices.demo-sensor-01
```

→ variable `@historianRuleMeta`

## 3. Chart widget

On a dashboard **chart** or **value** widget:

- **Object path:** device path
- **Variable:** output name from the rule (`avgTemp5m`, `derived-a`, … — not `derivedValue`)

**Multi-tag chart:** `chartStyle: line` + `analyticsQueryTagsJson` — see [cookbook § Dashboard binding](../../docs/en/analytics-historian-cookbook.md#dashboard-binding) and Recipe 5.

## 4. Tag chain (optional)

Three-level pipeline (sensor → smooth → smooth again) on separate devices. See cookbook **Recipe 3** and **Recipe 5** (`analytics-demo` on prod).

## REST API (current)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` / `PUT` | `/api/v1/objects/by-path/binding-rules?path=…` | List / save rules (reactive + historian) |
| `GET` | `/api/v1/platform/analytics/tags?path=…` | Tag catalog |
| `GET` | `/api/v1/platform/analytics/tags/by-path?path=…` | One tag (`objectPath#ruleId`) |
| `POST` | `/api/v1/platform/analytics/query` | Multi-tag aligned historian query (charts) |
| `POST` | `/api/v1/platform/analytics/expression/validate` | CEL + `hist.*` validation |

Deprecated for new work: `/api/v1/platform/analytics/templates/*`, `derived-tags/refresh` as primary workflow.

## Related

- [docs/en/analytics-historian-cookbook.md](../../docs/en/analytics-historian-cookbook.md)
- [docs/en/analytics-tag-catalog.md](../../docs/en/analytics-tag-catalog.md)
- [docs/en/decisions/0041-multi-tag-historian-computations.md](../../docs/en/decisions/0041-multi-tag-historian-computations.md)
