# ADR-0074: Historian quality persistence (sample v2)

## Status

**Accepted** (2026-10-13) — amends [0025a](0025-telemetry-quality-flags.md). Live `quality` stays Done. Flagship persisted quality (jdbc and ClickHouse) round-trips `GOOD` / `UNCERTAIN` / `BAD`. The ClickHouse proof is opt-in (`ISPF_CLICKHOUSE_QUALITY_TCK=true`), not a default CI job. Cassandra/Scylla stays experimental and quality-incapable. Cold Parquet export of `quality` remains a Gap. Scorecard Historian stays **9.5**.

## Context

Live variables carry quality flags (0025a). Before this slice the historian path was value-centric. Phase 28 closed query SLO tiers without quality columns.

## Decision

### 1. Target contract

1. Each stored sample may carry `quality` (enum/code compatible with live flags) beside `value`, `observed_at`, `sampled_at` / ingest time as already modeled.
2. Dual-write backends ([0035](0035-historian-dual-write.md)) must either persist quality on **both** or document the backend as quality-incapable (experimental).
3. A **TCK** (JUnit) asserts round-trip GOOD / UNCERTAIN / BAD for jdbc and for ClickHouse before claiming those stores Done. The ClickHouse container test is opt-in (see honesty). Default CI does not start ClickHouse.

### 2. Non-goals (this ADR)

- Changing live CEL quality semantics
- Compression/deadband algorithms (separate historian v2 epic)
- Raising scorecard Historian to 10 without TCK green

### 3. Rollout

- Expand schema (expand/contract per [0064](0064-release-compatibility-lts.md)).
- Backfill: null quality = “unknown / pre-v2”, not GOOD.

## Consequences

- Amends 0025a follow-up list.
- Flagship stores are **jdbc** (PostgreSQL/Timescale) and **ClickHouse**. **Cassandra/Scylla stays experimental** and quality-incapable ([0025b](0025-cassandra-scylla-timeseries-store.md)).
- Do not raise the Historian scorecard to 10 on this slice.

### Partial → Done (ClickHouse)

Wave 2B left the ClickHouse container row **Partial**: the column and JSON payload existed, and this repo had no Testcontainers (or other container) profile, so nothing proved GOOD / UNCERTAIN / BAD survived a real ClickHouse. That row is **Done** only as an opt-in container test. It is not a default CI gate. Treating a green default pipeline as a ClickHouse quality proof would be false. Scylla was not promoted; it stays experimental.

### Honesty — Done / Partial / Gap (Wave 2B)

| Claim | Status |
|-------|--------|
| Nullable `quality` on `variable_samples` (Flyway `V94`, PostgreSQL + H2 pack) | **Done** |
| JDBC write + query mapping; null means unknown / pre-v2, not GOOD | **Done** |
| TCK `HistorianQualityPersistenceTckTest`: GOOD / UNCERTAIN / BAD round-trip on jdbc | **Done** |
| Live row field `quality` copied onto samples | **Done** |
| ClickHouse Java bootstrap column + JSON insert (sole store, dual-write secondary, warm tier) | **Done** (schema/payload) |
| ClickHouse container round-trip TCK | **Done** (opt-in) — `HistorianQualityClickHouseTckTest` on `clickhouse/clickhouse-server:24.8` (same tag as `deploy/docker-compose.clickhouse.yml`) when `ISPF_CLICKHOUSE_QUALITY_TCK=true`. Default CI does not start ClickHouse; a green default `test` task does not re-prove this row |
| Cassandra/Scylla `quality` column | **Gap** — experimental backend, quality-incapable on purpose |
| Cold Parquet export of `quality` | **Gap** — not this slice |
| Dual-write secondary failure counter `ispf.historian.dual_write.secondary_failures` | **Done** (failure count, not a lag gauge — see [0035](0035-historian-dual-write.md)) |

## Related

- [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md), [0035-historian-dual-write](0035-historian-dual-write.md), [historian-tiers](../historian-tiers.md)
