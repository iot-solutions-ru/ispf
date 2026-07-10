# ADR-0016: ClickHouse SPI for event journal (P3b)

## Status

Accepted (2026-06-25)

## Context

P3a ([0015-event-history-timescale](0015-event-history-timescale.md)) moved `event_history` into a Timescale hypertable in PostgreSQL. At ~40 events/s, OLTP writes to PostgreSQL remain the bottleneck; for hundreds/thousands of events/s a column store with batch insert and separate scaling is needed.

PostgreSQL/Timescale remains for the relational core (object tree, workflow, RBAC, alert state).

## Decision

1. **`EventJournalStore` SPI** — single append/query point for the event journal.
2. **Backends:**
   - `jdbc` (default) — PostgreSQL/Timescale `event_history`, JDBC batch insert ([P2/P3a](0015-event-history-timescale.md)).
   - `clickhouse` — MergeTree via HTTP (`JSONEachRow`), no additional Java dependencies.
3. **Config:** `ispf.event-journal.store=jdbc|clickhouse`, nested `ispf.event-journal.clickhouse.*` (URL, database, table, credentials).
4. **Retention:** JDBC + Timescale → policy; JDBC without Timescale → scheduled purge; ClickHouse → `TTL occurred_at + INTERVAL N DAY` on table creation.
5. **Timescale `event_history` hypertable** is skipped when `store=clickhouse`.
6. **Hot path unchanged:** `EventJournalAsyncWriter` → `EventJournalBatchPersister` → store; `RecentEventCache` for UI/correlator.

## ClickHouse schema

```sql
CREATE TABLE ispf.event_history (
    id String,
    object_path String,
    event_name String,
    level LowCardinality(String),
    payload_json String,
    occurred_at DateTime64(3, 'UTC')
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (object_path, occurred_at, id)
TTL occurred_at + INTERVAL 90 DAY;
```

## Consequences

- Prod default remains `jdbc` (Timescale); ClickHouse is opt-in for high-throughput deployments.
- Local CH: [deploy/docker-compose.clickhouse.yml](../../deploy/docker-compose.clickhouse.yml).
- Metric `ispf.event_history.records` — O(1) counter on both backends.
- Correlator payload filter reads the latest event through the store (not JPA).

## Related

- [0015-event-history-timescale](0015-event-history-timescale.md) — Timescale tier (P3a)
- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) — automation pipeline evolution
- [load-testing](../load-testing.md) — throughput baselines
