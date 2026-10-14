> **Язык:** русская версия. Канон: [../../en/decisions/0074-historian-quality-persistence.md](../../en/decisions/0074-historian-quality-persistence.md).

# ADR-0074: Quality в историке (sample v2)

## Статус

**Accepted** (Wave 2B) — см. канонический EN-текст. jdbc и ClickHouse (opt-in `ISPF_CLICKHOUSE_QUALITY_TCK=true`, не default CI) гоняют `GOOD` / `UNCERTAIN` / `BAD`. Cassandra/Scylla остаётся experimental.

## Суть

Таблицы Done / Partial / Gap и hard rules — в EN. При расхождении побеждает EN.

## Связанное

[readme](readme.md) · [en/0074-historian-quality-persistence](../../en/decisions/0074-historian-quality-persistence.md)