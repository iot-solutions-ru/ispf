# ADR-0009: TimescaleDB retention для variable history

Статус: **Accepted**  
Дата: 2026-06-21

## Контекст

История переменных (`variable_samples`) растёт с числом устройств и частотой poll. Нужна политика хранения: платформенный default, per-variable override, hypertable в production.

## Решение

1. **Production storage** — PostgreSQL с расширением TimescaleDB; таблица `variable_samples` — hypertable (инициализация при старте `TimescaleHypertableInitializer`).
2. **Платформенный default** — `ispf.variable-history.retention-days` (default **90** дней); применяется, если у переменной `historyRetentionDays = null`.
3. **Per-variable override** — флаги `historyEnabled`, `historyRetentionDays` на переменной (и в model template); очистка scheduler учитывает оба уровня.
4. **Запись** — debounce по `ispf.variable-history.min-interval-ms` на событие `VARIABLE_UPDATED`; отключённая история не пишет samples.
5. **Federated paths** — historian proxy через federation API; retention остаётся на стороне источника данных (remote peer).
6. **Edge profile** — H2/SQLite без Timescale; retention policy та же логика, без hypertable chunking.

## Последствия

- [VARIABLE_HISTORY.md](../VARIABLE_HISTORY.md) — операторский и API контракт.
- [DEPLOYMENT.md](../DEPLOYMENT.md) — docker image `timescale/timescaledb` для prod.
- Увеличение retention на prod — ops: изменить config + при необходимости `SELECT drop_chunks` / политику Timescale (вне hot-path кода).

## Связанные материалы

- ROADMAP Phase 2.2 — TimescaleDB hypertables + retention
- REQ-PF variable history — [OBJECT_MODEL.md](../OBJECT_MODEL.md)
