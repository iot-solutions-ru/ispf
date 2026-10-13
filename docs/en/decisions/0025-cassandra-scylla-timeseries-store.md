# ADR-0025: Cassandra/Scylla SPI for time-series stores

> **Number alias:** **0025b** (Cassandra/Scylla timeseries). The same numeric id also labels [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md) (**0025a**). Prefer the **filename or alias** in new links; do not renumber files.

## Status

**Accepted** (2026-07-04) — backend available.  
**Support tier: experimental** — not a certified production default. Prefer `jdbc` (Timescale) or `clickhouse` unless the site already runs CQL.

## Context

ISPF stores high-frequency time-series in two paths:

1. **Event journal** — `EventJournalStore` ([0015](0015-event-history-timescale.md), [0016a](0016-clickhouse-event-journal.md)).
2. **Variable historian** — `VariableHistoryWriteStore` + `VariableHistoryQueryStore` ([0017](0017-telemetry-ingest-pipeline.md)).

Default backends: `jdbc` (PostgreSQL/Timescale) and opt-in `clickhouse`. Sites that already operate Cassandra or Scylla need a third backend **without** duplicating the hot path writers.

**Industry bar:** leaders offer a primary historian path plus optional adapters for incumbent infrastructure. ISPF’s primary story is Timescale/JDBC (+ ClickHouse); CQL is an adapter, not the flagship.

## Decision

1. **Store value:** `cassandra` or `scylla` (alias) — same Java backend (DataStax Java Driver 4.x).
2. **Config:**
   - `ispf.event-journal.store=jdbc|clickhouse|cassandra|scylla`
   - `ispf.variable-history.store=jdbc|jpa|clickhouse|cassandra|scylla`
   - nested `ispf.*.cassandra.*`: contact-points, port, local-datacenter, keyspace, table, username, password.
3. **Event journal schema (CQL):**
   - `event_history` — PK `((object_path), occurred_at, id)`
   - `event_history_global` — PK `((month_bucket), occurred_at, id)`
   - `event_journal_meta` — `total_count` counter
4. **Variable history schema:** `variable_samples` — PK `((object_path, variable_name, field_name), sampled_at)`.
5. **Retention:** per-row `USING TTL` from `retention-days`; application purge disabled (`supportsApplicationRetentionPurge=false`).
6. **Timescale hypertables** skipped for external stores (`clickhouse`, `cassandra`, `scylla`).
7. **Historian aggregations:** JVM bucket aggregation (same fallback as JDBC); no server-side GROUP BY.

### Honesty — support matrix

| Tier | Meaning | This backend |
|------|---------|--------------|
| **supported** (prod default path) | Documented, CI-exercised, scorecard-facing | `jdbc`, `clickhouse` (opt-in) |
| **experimental** | Shipped SPI; site-owned ops; no FIELD/CERTIFIED claim | **`cassandra` / `scylla`** |
| **unsupported** | Not in matrix | — |

Do **not** raise Historian scorecard on Cassandra/Scylla alone. Quality persist TCK ([0074](0074-historian-quality-persistence.md)) targets jdbc + ClickHouse first.

## Consequences

- Prod default remains `jdbc`; Cassandra/Scylla is opt-in for existing CQL clusters.
- Local: [deploy/docker-compose.scylla.yml](../../../deploy/docker-compose.scylla.yml); verify: `deploy/vps-cassandra-verify.sh`.
- Cite as **0025b** in new docs.

## Related

- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) (**0016a**)
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)
- [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md) (**0025a**)
- [0074-historian-quality-persistence](0074-historian-quality-persistence.md)
- [deployment](../deployment.md)
