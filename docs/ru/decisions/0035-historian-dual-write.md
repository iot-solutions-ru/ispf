> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0035-historian-dual-write.md](../../en/decisions/0035-historian-dual-write.md).

# ADR-0035: Historian dual-write (PostgreSQL + ClickHouse)

Статус: **Принято**  
Дата: 2026-07-06

## Контекст

Historian по умолчанию пишет в PostgreSQL/Timescale (`ispf.variable-history.store=jdbc`). ClickHouse уже поддерживается как **единственный** store (`store=clickhouse`) для high-throughput deployments ([0017](0017-telemetry-ingest-pipeline.md)).

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

## Последствия

- Дублирование storage; мониторинг lag/failures на CH append.
- Query migration — отдельный BL (optional read replica routing).
- `store=clickhouse` без dual-write — по-прежнему single-store mode.

См. [DEPLOYMENT.md § ClickHouse](../deployment.md), [ROADMAP BL-116](../roadmap.md).
