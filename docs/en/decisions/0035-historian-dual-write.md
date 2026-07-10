# ADR-0035: Historian dual-write (PostgreSQL + ClickHouse)

## Status

Accepted (2026-07-06)

## Context

Historian writes to PostgreSQL/Timescale by default (`ispf.variable-history.store=jdbc`). ClickHouse is already supported as the **sole** store (`store=clickhouse`) for high-throughput deployments ([0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)).

Operators need a migration path: **OLTP-consistent reads from PostgreSQL**, analytics/long retention in ClickHouse without cutover risk (BL-116).

## Decision

1. Flag **`ispf.variable-history.dual-write-enabled=true`** when **`store=jdbc`** (default).
2. **`DualWriteVariableHistoryWriteStore`** (@Primary): JDBC primary + best-effort ClickHouse secondary.
3. Secondary failures **log + swallow** — do not block telemetry hot path.
4. **Reads** remain on JDBC (`JdbcVariableHistoryQueryStore`) until explicit query backend switch.
5. Secondary uses existing block **`ispf.variable-history.clickhouse.*`** and MergeTree schema bootstrap.

## Configuration

```yaml
ispf:
  variable-history:
    store: jdbc
    dual-write-enabled: true
    clickhouse:
      url: http://localhost:8123
      database: ispf
      table: variable_samples
```

## Consequences

- Duplicate storage; monitor lag/failures on ClickHouse append.
- Query migration — separate BL (optional read replica routing).
- `store=clickhouse` without dual-write — still single-store mode.

See [DEPLOYMENT.md § ClickHouse](../deployment.md), [roadmap](../roadmap.md).

## Related

- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) — ClickHouse event journal
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md) — historian stores
- [clickhouse-prod-playbook](../clickhouse-prod-playbook.md)
