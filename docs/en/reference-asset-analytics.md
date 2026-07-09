> **Language:** Canonical English. Russian edition: [ru/reference-asset-analytics.md](../ru/reference-asset-analytics.md).

> **Deprecated for new configurations (ADR-0041).** Use historian binding rules and [analytics-historian-cookbook.md](analytics-historian-cookbook.md) instead of `ANALYTICS_TEMPLATE` + apply workflow.

# Asset analytics (BL-160 / BL-201)

AF-like **lite** framework: catalog KPI templates, historian rollups, runtime **derived tags** on devices, typed Explorer inspector, and apply workflow.

**Status:** BL-201 Done — editor + CRUD API + `derivedValue` runtime + example walkthrough.

## Object model

| Path | Type | Role |
| --- | --- | --- |
| `root.platform.analytics` | `ANALYTICS` | Catalog folder |
| `root.platform.analytics.<templateId>` | `ANALYTICS_TEMPLATE` | Reusable rollup/KPI definition |

Each template stores config variables: `templateId`, `helper`, `sourcePath`, `sourceVariable`, `sourceField`, `windowBucket`, `blueprintName`, `enabled`.

## Built-in templates

Registered at bootstrap (`AnalyticsBlueprintBootstrap`):

- **rollingAvg** — historian `avg` over `windowBucket` → device `derivedValue`
- **rateOfChange** — delta between first/last bucket avg → `derivedValue`
- **oee** — A×P×Q composite → `oeePct`, `availabilityPct`, `performancePct`, `qualityPct`

Linked RELATIVE blueprints: `rolling-avg-v1`, `rate-of-change-v1`, `oee-v1`.

## Derived tag runner

`AnalyticsDerivedTagRunner` (`@Scheduled`, leader lock) scans devices with analytics blueprint variables and writes runtime fields using `VariableHistoryService.aggregate`.

Config (`ispf.analytics`):

- `derived-tag-enabled` (default `true`)
- `derived-tag-tick-ms` (default `60000`)

Manual refresh: `POST /api/v1/platform/analytics/derived-tags/refresh?devicePath=…`

## Web console

- Explorer: `root.platform.analytics` → folder list; child template → **Analytics template** inspector (typed form, apply section, historian preview).
- Chart widgets: `analyticsTemplateId` selects helper + aggregate bucket ([widgets.md](widgets.md)).

## Apply workflow

`POST /api/v1/platform/analytics/templates/apply` body:

```json
{
  "templatePath": "root.platform.analytics.rollingAvg",
  "devicePath": "root.platform.devices.demo-sensor-01",
  "sourceVariable": "temperature",
  "sourcePath": "root.platform.devices.demo-sensor-01",
  "windowBucket": "5m"
}
```

OEE apply additionally requires `availabilityVariable`, `performanceVariable`, `qualityVariable`.

## Example

[examples/analytics-rolling-avg/README.md](../../examples/analytics-rolling-avg/README.md)

## Related

- [variable-history.md](variable-history.md) — aggregate buckets including `8h`
- Roadmap BL-160 (Phase 28), BL-201 (Phase 33 AF-lite completion)
- [analytics-platform-roadmap.md](analytics-platform-roadmap.md) — BL-200…210 enterprise analytics
