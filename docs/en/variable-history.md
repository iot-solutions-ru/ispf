> **Language:** Canonical English. Russian edition: [ru/variable-history.md](../ru/variable-history.md).

# Variable History (Time-Series Historian)

> **Status:** Stable — time-series recording, retention policies, interval aggregation, export, and query SLOs. Status tags: [doc-status.md](doc-status.md).

The variable history subsystem (*Historian*) records changes to selected object variables over time, serves raw samples and pre-computed bucket aggregates to dashboard charts and reports, and automatically prunes expired samples according to each variable's retention policy.

**Related guides:** [Object model](object-model.md) · [Historian storage tiers (PostgreSQL / ClickHouse / Parquet)](historian-tiers.md) · [Analytics calculation cookbook](analytics-historian-cookbook.md) · [ClickHouse production playbook](clickhouse-prod-playbook.md).

---

## 1. How Variable Recording Works

- **Storage table:** samples are written to `variable_samples` (PostgreSQL / TimescaleDB hypertable by default, or ClickHouse when configured).
- **Per-variable flags:** recording is controlled on each variable by `historyEnabled` (`true` / `false`) and `historyRetentionDays` (number of days to keep samples; `null` uses the server default `ispf.variable-history.retention-days`).
- **Sampling mode (`historySampleMode`):**
  - `CHANGES_ONLY` (default) — records a new point when the value changes (respecting `min-interval-ms`);
  - `ALL_VALUES` — records every incoming sample regardless of whether the value changed.
- **Sample quality (`quality`, [ADR-0074](decisions/0074-historian-quality-persistence.md)):** each stored sample carries `GOOD`, `UNCERTAIN`, `BAD`, or `NULL` (unknown / legacy). Trend charts automatically break the line on `BAD` samples.
- **Blueprint synchronization:** at server startup (`syncAllModelBackedVariableMetadata()`), history flags defined on blueprints are synchronized to all instances backed by those templates.

### Enabling History via REST API or Web Console

In the Web Console, open the object in the Inspector → **Variables** tab → click **History settings** on the variable. Via REST API:

```http
PATCH /api/v1/objects/by-path/variables/history?path=root.platform.devices.demo-sensor-01&name=temperature
Content-Type: application/json

{
  "historyEnabled": true,
  "historyRetentionDays": 30,
  "historySampleMode": "CHANGES_ONLY"
}
```

---

## 2. Querying Raw Samples, Aggregations, and Exports

| Operation | HTTP Endpoint | Description |
|-----------|---------------|-------------|
| **Raw samples** | `GET /api/v1/objects/by-path/variables/history?path=...&name=temperature&field=value&from=...&to=...&limit=500` | Returns raw points (up to `raw-query-max-points`, default `10000`) |
| **Bucket aggregation** | `GET /api/v1/objects/by-path/variables/history/aggregate?path=...&name=temperature&bucket=1m\|5m\|15m\|30m\|1h\|6h\|1d&from=...&to=...` | Returns `{ buckets: [{ ts, avg, min, max, count }] }` |
| **File export** | `GET /api/v1/objects/by-path/variables/history/export?path=...&name=temperature&format=csv\|json` | Downloads up to `10,000` points as a CSV or JSON attachment |

- **Multi-field variables (`field` parameter):** pass the numeric schema field name (default `"value"`). Pass `field=$record` (when `record-snapshot-enabled: true`) to retrieve the full `DataRecord` JSON snapshot.
- **Dashboard charts (`chart` and `sparkline` widgets):** set `historyRange` (`live`, `1h`, `6h`, `24h`, `7d`, `all`). Ranges up to `24h` load raw points; `7d` and `all` automatically query `1h` and `6h` bucket aggregates. Clicking the **History** button on a widget opens a modal with period selection, field selector, and CSV/JSON export.

---

## 3. Server Configuration (`application.yml`)

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90              # default when variable historyRetentionDays is null
    record-snapshot-enabled: true   # enables field=$record full DataRecord snapshots
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

When ClickHouse is the sole historian store, enable the spool so transient ClickHouse or network outages buffer samples on local disk and replay them once ClickHouse recovers. (In `jdbc` + dual-write mode, PostgreSQL is already the durable primary and `HistorianRangeCatchUpService` catches ClickHouse up after an outage.)

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

**Metrics:** `ispf.historian.spool.bytes`, `.lag`, `.oldest_age_ms`, `.replayed`, `.errors`, `.quota` (also under platform metrics `spool*`). Dual-write secondary failures and catch-up gauges: `ispf.historian.dual_write.*` (`oldest_age_ms` = sample-clock age of the oldest open catch-up range, not CH-vs-PG lag — [ADR-0035](decisions/0035-historian-dual-write.md)).

**Still deferred (do not implement in passing):** second copy of the historian buffer on another ISPF node; ISPF-managed ClickHouse cluster. See [known-limitations](known-limitations.md).

---

## 4. Query and Analytics Service-Level Objectives (SLO)

| Query Type | Workload Scope | Target Latency (p95) |
|------------|----------------|----------------------|
| **Single-tag bucket aggregate** | Up to 1,000,000 raw samples (`GET .../history/aggregate`) | **< 2 s** |
| **Raw trend query** | Up to 10,000 samples (`GET .../history`) | **< 500 ms** |
| **Multi-tag aligned query** | 10 tags × 7 days × `1h` buckets (`POST /api/v1/platform/analytics/query`) | **< 3 s** |
| **Rollup materializer lag** | Pre-computed rollup head vs live ingest | **< 5 min** |

Benchmark and verification scripts:
- `bash tools/historian-scale/historian-scale-benchmark.sh` — single-tag aggregate benchmark;
- `bash tools/historian-scale/analytics-scale-gate.sh` — combined multi-tag and aggregate gate;
- Live SLO status API: `GET /api/v1/platform/analytics/historian-sla` and `GET /api/v1/platform/analytics/analytics-slo`.
