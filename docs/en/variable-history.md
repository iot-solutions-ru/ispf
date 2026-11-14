> **Language:** Canonical English. Russian edition: [ru/variable-history.md](../ru/variable-history.md).

# Variable history — phases

> **Status:** Stable — Time-series, retention. Hub: [doc-status.md](doc-status.md).

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
- `field` parameter — select variable schema field (same as query); use `field=$record` for full variable snapshot (DataRecord JSON in `text`)
- In `VariableHistoryPanel`: field dropdown (if schema has multiple numeric fields) and **CSV** / **JSON** buttons; `$record` shows a scrollable JSON list instead of a chart

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
| Threshold / trend alerts | `ALERT` nodes in tree; see [automation](automation.md) |
| Event correlators | `CORRELATOR` nodes in tree; API `/api/v1/correlators` — see [automation](automation.md) |

Historian only **stores and serves** time series; event generation and escalation are the automation layer.

## Configuration

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90   # default when variable retention = null
    record-snapshot-enabled: true   # field $record — full DataRecord JSON in value_text
    # quality column (ADR-0074): GOOD | UNCERTAIN | BAD, or NULL = unknown / pre-v2 (not GOOD)
    # Flagship stores: jdbc and clickhouse. cassandra/scylla stay experimental and omit quality.
    # Per-variable policy (PATCH .../variables/history):
    #   historySampleMode: CHANGES_ONLY | ALL_VALUES
    #   includePreviousValueInEvent: boolean  # opt-in previousValue on WS/automation (ADR-0061)
    #   storageMode: PERSISTENT | TRANSIENT
    # High-rate MQTT: keep object eventJournalEnabled=false (anti-patterns.md / ADR-0061)
    # Disk spool (sole-store ClickHouse only; ignored unless store=clickhouse). Off by default.
    # store: clickhouse
    # spool:
    #   enabled: true
    #   directory: /opt/ispf/data/historian-spool   # use a persistent volume in containers
    #   max-bytes: 8589934592   # 8 GiB; fail-closed when full
    #   segment-bytes: 67108864
    #   fsync: true
    #   replay-batch: 32
    #   replay-interval-ms: 1000
    slo:
      aggregate-max-points: 1000000
      aggregate-max-latency-ms: 2000
      raw-query-max-points: 10000
      raw-query-max-latency-ms: 500
      export-max-points: 10000
```

## Disk spool (sole-store ClickHouse)

Local store-and-forward in front of **`store=clickhouse`**. **Off by default.** Ignored for `jdbc` / dual-write (those use the JDBC primary + catch-up WAL). Quota is **fail-closed**: when `max-bytes` is hit, new accepts are rejected until replay frees space. With `async-enabled=true` (default), that rejection holds writer batches and applies blocking backpressure once the in-memory queue fills (no drop-oldest).

| Key / env | Default | Notes |
|-----------|---------|-------|
| `ispf.variable-history.spool.enabled` / `ISPF_VARIABLE_HISTORY_SPOOL_ENABLED` | `false` | Enable only after CH cutover |
| `…spool.directory` / `ISPF_VARIABLE_HISTORY_SPOOL_DIRECTORY` | `data/historian-spool` | Relative to process cwd unless absolute |
| `…spool.max-bytes` / `ISPF_VARIABLE_HISTORY_SPOOL_MAX_BYTES` | `8589934592` (8 GiB) | Hard quota |
| `…spool.segment-bytes` / `ISPF_VARIABLE_HISTORY_SPOOL_SEGMENT_BYTES` | `67108864` (64 MiB) | Segment size |
| `…spool.fsync` / `ISPF_VARIABLE_HISTORY_SPOOL_FSYNC` | `true` | Durability vs throughput |
| `…spool.replay-batch` / `ISPF_VARIABLE_HISTORY_SPOOL_REPLAY_BATCH` | `32` | Replay batch size |
| `…spool.replay-interval-ms` / `ISPF_VARIABLE_HISTORY_SPOOL_REPLAY_INTERVAL_MS` | `1000` | Replay interval |

**Containers / Helm:** data PVC mounts at `/opt/ispf/data`. Set directory to `/opt/ispf/data/historian-spool` (or another path on that volume). A relative `data/…` path only survives restarts if the process cwd already lives on the PVC.

**Metrics:** `ispf.historian.spool.bytes`, `.lag`, `.oldest_age_ms`, `.replayed`, `.errors`, `.quota` (also under platform metrics `spool*`). Dual-write secondary failures and catch-up lag gauges: `ispf.historian.dual_write.*` ([ADR-0035](decisions/0035-historian-dual-write.md)).

**Still deferred (do not implement in passing):** second copy of the historian buffer on another ISPF node; ISPF-managed ClickHouse cluster. See [known-limitations](known-limitations.md).

## Query SLO (BL-161)

Documented service-level objectives for historian REST queries. Defaults bind via `VariableHistorySloProperties` (`ispf.variable-history.slo`).

| Query | Scope | Target (p95) |
|-------|-------|--------------|
| **Aggregate** | ≤ 1M raw samples bucketed (`GET .../history/aggregate`) | **< 2 s** |
| **Raw trend** | ≤ 10k points (`GET .../history`) | **< 500 ms** |
| **Export** | ≤ 10k points (`GET .../history/export`) | best-effort; same point cap |

**CI / lab gates (BL-161 Done):**

| Gate | Path | Target |
|------|------|--------|
| JVM aggregate | `HistorianAggregateQueryLoadTest` via `tools/historian-scale/historian-scale-benchmark.sh` | ≤1M points, **p95 &lt; 2 s** |
| Nightly workflow | `.github/workflows/load-test.yml` | same JVM gate |
| Combined analytics | `tools/historian-scale/analytics-scale-gate.sh` | aggregate + multi-tag; optional live CH/catalog |

## Analytics SLO (BL-210)

Documented service-level objectives for the **analytics platform** plane (multi-tag query, catalog scale, materializer lag). Defaults bind via `AnalyticsSloProperties` (`ispf.analytics.slo`).

| Gate | Scope | Target |
|------|-------|--------|
| **Multi-tag query** | 10 tags × 7 calendar days × 1h buckets (`POST .../platform/analytics/query`) | **p95 < 3 s** (8 vCPU ClickHouse lab) |
| **Catalog** | History-enabled tags under lab prefix | **≥ 50k** devices (Enterprise L) |
| **ClickHouse samples** | `variable_samples` row count | **≥ 1B** rows (Enterprise L ingest replay) |
| **Materializer lag** | Rollup head vs historian ingest | **< 5 min** behind wall clock |
| **Single-tag aggregate** | ≤ 1M points (BL-161) | **p95 < 2 s** (unchanged) |

Tracked scripts (prefer these over gitignored `deploy/local/tools/` copies):

- `tools/historian-scale/analytics-scale-gate.sh` — multi-tag + aggregate JVM gates; optional catalog/CH
- `tools/historian-scale/historian-scale-benchmark.sh` — BL-161 aggregate only
- JVM CI: `AnalyticsMultiTagQueryLoadTest`, `HistorianAggregateQueryLoadTest` (`@Tag("load")`, `load-test.yml`)

SLO targets API: `GET /api/v1/platform/analytics/analytics-slo`. Historian + analytics targets also appear under `analyticsSlo` in `GET .../historian-sla`.

Walkthroughs: [examples/analytics-platform/site-m](../../examples/analytics-platform/site-m/), [enterprise-l](../../examples/analytics-platform/enterprise-l/). Gap register: [analytics-platform-gaps](analytics-platform-gaps.md).

Dashboard reference: [examples/historian-sla-dashboard](../../examples/historian-sla-dashboard/) (BL-161 widget layout + BFF sketch).

Multi-tier retention and deploy profiles: [historian-tiers](historian-tiers.md).

See also [object-model](object-model.md), [api](api.md).
