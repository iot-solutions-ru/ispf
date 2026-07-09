# Analytics tag catalog (BL-209 / ADR-0041)

Deployed **historian computations** are discovered from `@bindingRules` entries with `kind: historian` on `DEVICE` objects. Each rule produces one catalog tag and one live output variable.

**Cookbook (OEE, tag chains):** [analytics-historian-cookbook.md](analytics-historian-cookbook.md)

## Tag identity

| Field | Value |
|-------|--------|
| Tag path | `objectPath#ruleId` (composite key for DAG, catalog, schedules) |
| Object path | Device that owns the rule and output variable |
| Output variable | `target.variableName` (arbitrary; multiple rules per device allowed) |
| Rule id | Stable id inside `@bindingRules` on that device |

Example: rule `avg-temp-5m` on `root.platform.devices.sensor-a` → tag `root.platform.devices.sensor-a#avg-temp-5m` → live var `avgTemp5m`.

## Per-rule metadata

Stored in system variable `@historianRuleMeta` (JSON object keyed by rule id):

| Key | Purpose |
|-----|---------|
| `quality` | `ok`, `uncertain`, `error`, `disabled` |
| `lastEvalAt` | ISO-8601 timestamp of last engine evaluation |
| `lastEvalStatus` | `ok`, `error`, `skipped` |

Updated by the analytics engine on each tick; read by catalog API and **Computations** inspector.

## REST API

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/platform/analytics/tags?path=` | operator+ | List deployed tags under optional path prefix |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | operator+ | Single tag; `path` = `objectPath#ruleId` or device path |
| POST | `/api/v1/platform/analytics/tags/backfill?path=&from=&to=` | developer+ | Recompute historian window for one tag |
| POST | `/api/v1/platform/analytics/expression/validate` | developer+ | Validate CEL-over-historian expression (BL-211) |
| POST | `/api/v1/platform/analytics/expression/evaluate` | developer+ | Evaluate expression once (BL-211) |
| POST | `/api/v1/platform/analytics/query` | operator+ | Multi-tag aligned historian query (BL-206) |

Alias paths `/derived-tags/catalog` may exist on some deployments for WAF compatibility.

### Catalog entry fields

`AnalyticsTagCatalogEntry` includes:

- `path` — composite tag path
- `helper` — `rollingAvg`, `rateOfChange`, `oee`, `cel`, …
- `expression` — human-readable formula
- `outputVariable` — live variable name on the device
- `sources` — historian/live inputs
- `upstreamTagPaths` / `downstreamTagPaths` — tag-to-tag dependencies
- `lineage` — graph for Explorer / impact analysis
- `qualityStatus` — propagated when upstream tags are disabled or uncertain

### CEL-over-historian (BL-211)

Rules whose expression contains `hist.*` compile as helper `cel`:

```text
hist.avg('root.platform.devices.sensor-a', 'temperature', '5m')
hist.live('root.platform.devices.sensor-a', 'temperature')
(hist.avg('…sensor-a', 'temperature', '5m') + hist.avg('…sensor-b', 'temperature', '5m')) / 2.0
```

Functions: `hist.avg`, `hist.min`, `hist.max`, `hist.last`, `hist.sum`, `hist.live`.

## Quality propagation

When an upstream analytics tag is **disabled** or has quality `uncertain` / `error`, dependent tags in the DAG are marked **`uncertain`** before the next evaluation.

## Haystack export

Output variables can carry Haystack point mappings (`point`, `cur`, `his`) via `driverPointMappingsJson` on the device variable.

## Explorer / UI

Object inspector → **Computations** tab:

1. **Rules** — unified list of reactive + historian `@bindingRules` (presets for rolling avg, OEE, CEL)
2. **Historian status** — catalog rows for this device (expression, quality)
3. Binding invoke journal (audit)

Expression debugger remains a separate tab.

## Deprecated (ADR-0041)

- Detecting tags by `derivedValue` / `oeePct` only
- `ANALYTICS_TEMPLATE` under `root.platform.analytics`
- Per-device `analyticsExpression` / `analyticsHelper` as source of truth
- `POST /api/v1/platform/analytics/templates/apply`

Legacy BL-160 docs: [reference-asset-analytics.md](reference-asset-analytics.md).

## Related

- [ADR-0041](decisions/0041-multi-tag-historian-computations.md)
- [ADR-0040](decisions/0040-unified-computations-ui.md)
- [ADR-0038](decisions/0038-analytics-platform-architecture.md)
- [analytics-historian-cookbook.md](analytics-historian-cookbook.md)
- [analytics-platform-roadmap.md](analytics-platform-roadmap.md) BL-209 / BL-211
