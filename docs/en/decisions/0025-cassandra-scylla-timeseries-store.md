# ADR-0025: Cassandra/Scylla SPI for time-series stores

## Status

Accepted (2026-07-04)

## Context

ISPF stores high-frequency time-series in two paths:

1. **Event journal** — `EventJournalStore` ([0015-event-history-timescale](0015-event-history-timescale.md), [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)).
2. **Variable historian** — `VariableHistoryWriteStore` + `VariableHistoryQueryStore` ([0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md), BL-40).

Current backends: `jdbc` (PostgreSQL/Timescale, default) and `clickhouse` (opt-in). For deployments that already run Cassandra or Scylla (CQL-compatible cluster), a third backend is needed without duplicating the hot path (`EventJournalAsyncWriter`, `VariableHistoryAsyncWriter`).

PostgreSQL remains for the relational core (object tree, workflow, RBAC).

## Decision

1. **Store value:** `cassandra` or `scylla` (alias) — same Java backend (DataStax Java Driver 4.x).
2. **Config:**
   - `ispf.event-journal.store=jdbc|clickhouse|cassandra|scylla`
   - `ispf.variable-history.store=jdbc|jpa|clickhouse|cassandra|scylla`
   - nested `ispf.*.cassandra.*`: contact-points, port, local-datacenter, keyspace, table, username, password.
3. **Event journal schema (CQL):**
   - `event_history` — PK `((object_path), occurred_at, id)` for per-object recent queries.
   - `event_history_global` — PK `((month_bucket), occurred_at, id)` for global recent feed.
   - `event_journal_meta` — `total_count` counter for metrics/bootstrap.
4. **Variable history schema:**
   - `variable_samples` — PK `((object_path, variable_name, field_name), sampled_at)`.
5. **Retention:** per-row `USING TTL` from `retention-days`; application purge disabled (`supportsApplicationRetentionPurge=false`).
6. **Timescale hypertables** skipped for any external store (`clickhouse`, `cassandra`, `scylla`).
7. **Historian aggregations:** JVM bucket aggregation (same fallback as JDBC store), no server-side GROUP BY.

## Consequences

- Prod default remains `jdbc`; Cassandra/Scylla is opt-in for existing CQL clusters.
- Local dev: [deploy/docker-compose.scylla.yml](../../../deploy/docker-compose.scylla.yml).
- Verify: `deploy/vps-cassandra-verify.sh`.
- ClickHouse and Cassandra can be combined (e.g. journal → Scylla, historian → ClickHouse).

## Related

- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)
- [deployment](../deployment.md)
