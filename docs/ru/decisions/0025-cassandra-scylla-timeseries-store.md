> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0025-cassandra-scylla-timeseries-store.md](../../en/decisions/0025-cassandra-scylla-timeseries-store.md).

# ADR-0025: Cassandra/Scylla SPI для time-series хранилищ

Статус: **Принято**  
Дата: 2026-07-04

## Контекст

ISPF хранит высокочастотные time-series в двух контурах:

1. **Event journal** — `EventJournalStore` ([0015-event-history-timescale](0015-event-history-timescale.md), [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)).
2. **Variable historian** — `VariableHistoryWriteStore` + `VariableHistoryQueryStore` ([0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md), BL-40).

Сейчас backend'ы: `jdbc` (PostgreSQL/Timescale, default) и `clickhouse` (opt-in). Для деплоев, где уже есть Cassandra или Scylla (CQL-совместимый кластер), нужен третий backend без дублирования hot path (`EventJournalAsyncWriter`, `VariableHistoryAsyncWriter`).

PostgreSQL остаётся для relational core (дерево объектов, workflow, RBAC).

## Решение

1. **Store value:** `cassandra` или `scylla` (alias) — один и тот же Java backend (DataStax Java Driver 4.x).
2. **Конфиг:**
   - `ispf.event-journal.store=jdbc|clickhouse|cassandra|scylla`
   - `ispf.variable-history.store=jdbc|jpa|clickhouse|cassandra|scylla`
   - nested `ispf.*.cassandra.*`: contact-points, port, local-datacenter, keyspace, table, username, password.
3. **Event journal schema (CQL):**
   - `event_history` — PK `((object_path), occurred_at, id)` для per-object recent queries.
   - `event_history_global` — PK `((month_bucket), occurred_at, id)` для global recent feed.
   - `event_journal_meta` — счётчик `total_count` для metrics/bootstrap.
4. **Variable history schema:**
   - `variable_samples` — PK `((object_path, variable_name, field_name), sampled_at)`.
5. **Retention:** per-row `USING TTL` из `retention-days`; application purge отключён (`supportsApplicationRetentionPurge=false`).
6. **Timescale hypertables** пропускаются при любом external store (`clickhouse`, `cassandra`, `scylla`).
7. **Historian aggregations:** JVM bucket aggregation (как fallback в JDBC store), без server-side GROUP BY.

## Последствия

- Prod default остаётся `jdbc`; Cassandra/Scylla — opt-in для существующих CQL-кластеров.
- Локальный dev: [deploy/docker-compose.scylla.yml](../../deploy/docker-compose.scylla.yml).
- Verify: `deploy/vps-cassandra-verify.sh`.
- ClickHouse и Cassandra можно комбинировать (например journal → Scylla, historian → ClickHouse).

## Связанные материалы

- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md)
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)
- [deployment](../deployment.md)
