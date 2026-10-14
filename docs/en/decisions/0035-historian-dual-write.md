# ADR-0035: Historian dual-write (PostgreSQL + ClickHouse)

## Status

**Accepted** (2026-07-06) — intentional **best-effort secondary**; not a certified two-phase commit.

## Context

Historian writes to PostgreSQL/Timescale by default (`ispf.variable-history.store=jdbc`). ClickHouse is already supported as the **sole** store (`store=clickhouse`) for high-throughput deployments ([0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)).

Operators need a migration path: **OLTP-consistent reads from PostgreSQL**, analytics/long retention in ClickHouse without cutover risk (BL-116).

## Decision

1. Flag **`ispf.variable-history.dual-write-enabled=true`** when **`store=jdbc`** (default).
2. **`DualWriteVariableHistoryWriteStore`** (@Primary): JDBC primary + best-effort ClickHouse secondary.
3. Secondary failures **log + swallow** — do **not** block the telemetry hot path. This is an explicit tradeoff, not an oversight.
4. **Reads** remain on JDBC (`JdbcVariableHistoryQueryStore`) until an explicit query-backend switch.
5. Secondary uses existing block **`ispf.variable-history.clickhouse.*`** and MergeTree schema bootstrap.

### Honesty — Done / Partial / Gap

| Claim | Status | Notes |
|-------|--------|-------|
| Dual-write flag + primary JDBC / secondary CH | **Done** | |
| Hot path never blocked by CH failure | **Done** (by design) | |
| Secondary append **failure** counter | **Done** | `ispf.historian.dual_write.secondary_failures` increments once per failed ClickHouse append after the JDBC primary succeeds. Not a lag gauge and not an alert rule. |
| Automatic **catch-up / backfill** after CH outage | **Gap** | Manual or separate BL |
| Query cutover to CH | **Partial** / separate BL | Reads stay JDBC until switched |

Do **not** claim “strong consistency across PG and CH” or “zero sample loss on secondary” from dual-write alone. Full DR tiers: [0068](0068-backup-and-dr.md).

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

- Duplicate storage; monitor lag/failures on ClickHouse append until Gap metrics land.
- Query migration — separate BL (optional read replica routing).
- `store=clickhouse` without dual-write — still single-store mode.
- Failure counter `ispf.historian.dual_write.secondary_failures` is registered on the Micrometer registry. Lag versus primary and an alert on that counter are still open.

See [deployment](../deployment.md) § ClickHouse, [roadmap](../roadmap.md).

## Related

- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) — ClickHouse event journal (**0016a**)
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md) — historian stores
- [0068-backup-and-dr](0068-backup-and-dr.md) — T3 historian backup honesty
- [clickhouse-prod-playbook](../clickhouse-prod-playbook.md)
