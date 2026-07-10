# ADR-0015: TimescaleDB tier for event journal

## Status

Accepted (2026-06-25)

## Context

The event journal (`event_history`) is an append-only stream: alert rules, API `POST /events/fire`, correlators. On load tests with internal automation (~60 virtual devices), writes to PostgreSQL (`INSERT` into `event_history`) became the bottleneck, while relational data (object tree, workflow, RBAC) remains OLTP workload.

Telemetry historian (`variable_samples`) already uses a TimescaleDB hypertable ([0009-timescaledb-retention](0009-timescaledb-retention.md)). The event journal previously lived in a plain PostgreSQL table.

## Decision

1. **`event_history` as a Timescale hypertable** on column `occurred_at` (initialized at startup, same pattern as `variable_samples`).
2. **Composite PK** `(occurred_at, id)` — Timescale requirement; JPA still uses `id` as `@Id` (same pattern as historian).
3. **Retention** — `ispf.event-journal.retention-days` (default **90**); on prod with Timescale — `add_retention_policy`; without Timescale — scheduled purge in the application.
4. **Compression** (optional) — segment by `object_path`, policy after 7 days; if the function is unavailable — skip without failing startup.
5. **PostgreSQL** remains for the relational core; column store (ClickHouse) — P3b ([0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)).

## Consequences

- Prod Docker: `timescale/timescaledb` image (already in [deployment](../deployment.md)).
- Flyway does not call `create_hypertable` — table DDL only; hypertable creation via `TimescaleHypertableInitializer` on `ApplicationReady`.
- Edge (H2/SQLite): hypertable skipped; retention via application purge.
- Metric `ispf.event_history.records` — O(1) counter; retention chunks do not require `COUNT(*)`.

## Related

- [automation](../automation.md) — event journal API
- [load-testing](../load-testing.md) — throughput baselines
- [object-model](../object-model.md) — persistence tables
