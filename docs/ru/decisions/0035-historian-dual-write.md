> **Язык:** русская версия. Английский оригинал: [en/decisions/0035-historian-dual-write.md](../../en/decisions/0035-historian-dual-write.md).

# ADR-0035: Historian dual-write (PostgreSQL + ClickHouse)

Статус: **Принято**  
Дата: 2026-07-06

## Контекст

Historian по умолчанию пишет в PostgreSQL/Timescale (`ispf.variable-history.store=jdbc`). ClickHouse уже поддерживается как **единственный** store (`store=clickhouse`) для high-throughput deployments ([0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md)).

Операторам нужен путь миграции: **OLTP-консистентные reads из PG**, аналитика/долгий retention в CH без cutover риска (BL-116).

## Решение

1. Флаг **`ispf.variable-history.dual-write-enabled=true`** при **`store=jdbc`** (default).
2. **`DualWriteVariableHistoryWriteStore`** (@Primary): JDBC primary + best-effort ClickHouse secondary.
3. Secondary failures **log + swallow** — не блокируют hot path телеметрии.
4. **Reads** остаются на JDBC (`JdbcVariableHistoryQueryStore`) до явного переключения query backend.
5. Secondary использует существующий блок **`ispf.variable-history.clickhouse.*`** и bootstrap схемы MergeTree.

## Конфигурация

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

## Честность — Done / Partial / Gap

| Заявление | Статус | Заметки |
|-----------|--------|---------|
| Dual-write flag + primary JDBC / secondary CH | **Done** | |
| Hot path не блокируется отказом CH | **Done** (by design) | |
| Счётчик отказов secondary append | **Done** | `ispf.historian.dual_write.secondary_failures` |
| Catch-up **gauges** | **Done** | `…catchup.ranges`, `.oldest_age_ms` (возраст open range по sample clock, не CH-vs-PG lag), `.wal_bytes` |
| Опциональные Prometheus **alert rules** | **Done** (lab) | [`deploy/prometheus-ispf-alerts.yml`](../../../deploy/prometheus-ispf-alerts.yml) — оператор подключает `rule_files` |
| Авто catch-up / backfill после outage CH | **Done** | Disk WAL + `historian_catchup_ranges` |
| Query cutover на CH | **Partial** | Reads остаются на JDBC до явного switch |

## Последствия

- Дублирование storage; мониторить failure counter + catch-up gauges (и optional Prometheus rules).
- Query migration — отдельный BL (optional read replica routing).
- `store=clickhouse` без dual-write — single-store; опциональный локальный [disk spool](../variable-history.md#disk-spool-sole-store-clickhouse) отдельно (`ispf.variable-history.spool`, выкл. по умолчанию).

См. [deployment](../deployment.md) § ClickHouse, [roadmap](../roadmap.md). Канон EN: [0035](../../en/decisions/0035-historian-dual-write.md).
