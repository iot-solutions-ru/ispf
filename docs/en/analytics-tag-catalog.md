# Analytics tag catalog (BL-209)

Deployed analytics tags carry metadata via RELATIVE blueprint **`analytics-tag-v1`** on `DEVICE` objects that expose `derivedValue` or `oeePct`.

## Metadata variables

| Variable | Purpose |
|----------|---------|
| `analyticsExpression` | Human-readable expression, e.g. `rollingAvg(root.devices.sensor.temperature, 5m)` |
| `analyticsHelper` | Engine helper (`rollingAvg`, `rateOfChange`, `oee`) |
| `analyticsLineageJson` | Comma-separated upstream tag paths (impact analysis) |
| `analyticsQuality` | `ok`, `uncertain`, `error`, `disabled` |
| `analyticsLastEvalAt` | ISO-8601 timestamp of last engine evaluation |
| `analyticsLastEvalStatus` | `ok`, `error`, `skipped` |
| `analyticsTagEnabled` | When `false`, tag is excluded from engine ticks and quality is `disabled` |
| `analyticsHaystackTags` | Haystack tags for output point export (`point,cur,his`) |

Blueprint is applied automatically when a template is deployed to a device or when the engine records an evaluation.

## REST API

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/platform/analytics/tags?path=` | operator+ | List deployed tags under optional path prefix |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | operator+ | Single tag with lineage graph and downstream impact |

Response includes:

- `sources` — historian/live inputs
- `upstreamTagPaths` / `downstreamTagPaths` — tag-to-tag chain
- `lineage.nodes` / `lineage.edges` — graph for Explorer inspector
- `qualityStatus` — propagated when upstream tags are disabled or uncertain

## Quality propagation

When an upstream analytics tag is **disabled** (`analyticsTagEnabled=false`) or has quality `uncertain` / `error`, all dependent tags in the DAG are marked **`uncertain`** before the next evaluation.

## Haystack export

Output variables (`derivedValue`, `oeePct`) receive Haystack point mappings with `point`, `cur`, and `his` tags via `driverPointMappingsJson` so semantic export includes analytics outputs.

## Explorer

Select a device with `derivedValue` or `oeePct` in the object tree to open the **Analytics tag** inspector: expression, sources, last evaluation, lineage graph, and downstream impact list.

See [ADR-0038](decisions/0038-analytics-platform-architecture.md) and [analytics-platform-roadmap.md](analytics-platform-roadmap.md) BL-209.
