# ADR-0016: ClickHouse SPI for event journal (P3b)

> **Number alias:** **0016a** (ClickHouse event journal). The same numeric id also labels [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) (**0016b**). Prefer the **filename or alias** in new links; do not renumber files. Registry hygiene (#458) treats this as a permanent citation rule, not a defect.

## Status

**Accepted** (2026-06-25) — dual-backend event journal SPI shipped. Default remains JDBC/Timescale; ClickHouse is the high-rate opt-in.

## Context

P3a ([0015-event-history-timescale](0015-event-history-timescale.md)) moved `event_history` into a Timescale hypertable in PostgreSQL. At ~40 events/s, OLTP writes to PostgreSQL remain the bottleneck; for hundreds/thousands of events/s a column store with batch insert and separate scaling is needed.

PostgreSQL/Timescale remains for the relational core (object tree, workflow, RBAC, alert state).

**Industry bar:** mature SCADA / historian stacks separate OLTP metadata from high-rate event or sample stores. ISPF matches that pattern with one SPI and two backends instead of a second product.

## Decision

1. **`EventJournalStore` SPI** — single append/query point for the event journal.
2. **Backends:**
   - `jdbc` (default) — PostgreSQL/Timescale `event_history`, JDBC batch insert ([P3a](0015-event-history-timescale.md)).
   - `clickhouse` — MergeTree via HTTP (`JSONEachRow`), no additional Java dependencies.
3. **Config:** `ispf.event-journal.store=jdbc|clickhouse`, nested `ispf.event-journal.clickhouse.*` (URL, database, table, credentials).
4. **Retention:** JDBC + Timescale → policy; JDBC without Timescale → scheduled purge; ClickHouse → `TTL occurred_at + INTERVAL N DAY` on table creation.
5. **Timescale `event_history` hypertable** is skipped when `store=clickhouse`.
6. **Hot path unchanged:** `EventJournalAsyncWriter` → `EventJournalBatchPersister` → store; `RecentEventCache` for UI/correlator.

### Honesty — Done / Partial / Gap

| Claim | Status |
|-------|--------|
| SPI + JDBC default + ClickHouse opt-in | **Done** |
| Same writer/cache hot path for both | **Done** |
| Prod playbook for ClickHouse journal | **Done** ([clickhouse-prod-playbook](../clickhouse-prod-playbook.md)) |
| Dual-write journal JDBC+CH | **Not claimed** (historian dual-write is [0035](0035-historian-dual-write.md)) |
| Cassandra/Scylla journal backend | **Experimental** — [0025b](0025-cassandra-scylla-timeseries-store.md) |

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
- Local CH: [deploy/docker-compose.clickhouse.yml](../../../deploy/docker-compose.clickhouse.yml).
- Metric `ispf.event_history.records` — O(1) counter on both backends.
- Correlator payload filter reads the latest event through the store (not JPA).
- Cite as **0016a** in new docs; never “ADR-0016” alone.

## Related

- [0015-event-history-timescale](0015-event-history-timescale.md) — Timescale tier (P3a)
- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) — automation pipeline evolution
- [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) — **0016b** (different topic, same number)
- [0025-cassandra-scylla-timeseries-store](0025-cassandra-scylla-timeseries-store.md) — **0025b**
- [load-testing](../load-testing.md) — throughput baselines
