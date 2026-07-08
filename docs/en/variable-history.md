> **Language:** Canonical English. Russian edition: [ru/variable-history.md](../ru/variable-history.md).

# Variable history — phases

## Phase 1 — Storage and write (done)

- Table `variable_samples`, write on `VARIABLE_UPDATED` with debounce
- Variable flags: `historyEnabled`, `historyRetentionDays`
- REST: `GET/PATCH .../variables/history`
- Scheduled cleanup respecting each variable's retention

## Phase 2 — UI configuration (done)

- Object editor, inspector, model editor
- Variable settings dialog

## Phase 3 — History viewer (done)

- `VariableHistoryPanel` component with period selection (1 h … 7 d … all)
- Object inspector: **Chart** button for variables with `historyEnabled`
- `useVariableHistory` hook for custom screens

## Phase 4 — Dashboard integration (done)

- `useTrendSeries` loads server history only if `historyEnabled`
- Chart/sparkline widgets show a hint when history is disabled
- API query returns empty array when history is off

## Phase 5 — Model sync (done)

- On start: `syncAllModelBackedVariableMetadata()` for all objects with `templateId`
- History metadata pulled from model and persisted in DB

## Phase 6 — Export and schema field (done)

- REST: `GET .../variables/history/export?format=csv|json` (up to 10,000 points, `Content-Disposition: attachment`)
- `field` parameter — select variable schema field (same as query)
- In `VariableHistoryPanel`: field dropdown (if schema has multiple numeric fields) and **CSV** / **JSON** buttons

## Phase 7 — Aggregations (done)

- REST: `GET .../variables/history/aggregate?bucket=1m|5m|15m|30m|1h|6h|1d`
- Response: `{ buckets: [{ ts, avg, min, max, count }] }`
- Period: `from` / `to` (if `from` omitted — from variable retention)
- UI: for **7 d** and **All** ranges chart uses averages (`1h` and `6h` respectively)

## Phase 8 — Historian on dashboards (done)

- **chart** and **sparkline** widgets: `historyRange` property (`live`, `1h` … `all`) in widget editor
- `live` — last N points with live tail (as before)
- Periods **1h–24h** — raw points from server; **7d** / **all** — avg/min/max aggregates
- **History** button on widget → modal with full panel (chart, period, field, export)

Historian is **complete** for current scope. Platform health monitoring — **System** tab in admin console (`GET /api/v1/platform/metrics`), not separate Prometheus historian metrics. Scraping `/actuator/prometheus` remains for external JVM/Spring monitoring.

## Roadmap (outside historian)

The following topics are **not** part of the variable history module — separate platform objects and services:

| Topic | Note |
|-------|------|
| Threshold / trend alerts | `ALERT` nodes in tree; see [AUTOMATION.md](automation.md) |
| Event correlators | `CORRELATOR` nodes in tree; API `/api/v1/correlators` — see [AUTOMATION.md](automation.md) |

Historian only **stores and serves** time series; event generation and escalation are the automation layer.

## Configuration

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90   # default when variable retention = null
    slo:
      aggregate-max-points: 1000000
      aggregate-max-latency-ms: 2000
      raw-query-max-points: 10000
      raw-query-max-latency-ms: 500
      export-max-points: 10000
```

## Query SLO (BL-161)

Documented service-level objectives for historian REST queries. Defaults bind via `VariableHistorySloProperties` (`ispf.variable-history.slo`).

| Query | Scope | Target (p95) |
|-------|-------|--------------|
| **Aggregate** | ≤ 1M raw samples bucketed (`GET .../history/aggregate`) | **< 2 s** |
| **Raw trend** | ≤ 10k points (`GET .../history`) | **< 500 ms** |
| **Export** | ≤ 10k points (`GET .../history/export`) | best-effort; same point cap |

Lab gate (Phase 28): `deploy/run_lab_historian_*.py` scripts should assert aggregate latency against `aggregate-max-latency-ms` at `aggregate-max-points` load.

Dashboard reference: [examples/historian-sla-dashboard](../examples/historian-sla-dashboard/) (BL-161 widget layout + BFF sketch).

Multi-tier retention and deploy profiles: [HISTORIAN_TIERS.md](historian-tiers.md).

See also [OBJECT_MODEL.md](object-model.md), [API.md](api.md).
