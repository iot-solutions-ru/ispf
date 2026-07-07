# ADR-0037: Relational core portability

Статус: **Accepted**  
Дата: 2026-07-07

## Контекст

ISPF хранит конфигурацию платформы (дерево объектов, RBAC, workflow, federation, cluster jobs) в **одном** relational backend через Flyway + JPA/JDBC. Time-series (журнал событий, история переменных) уже абстрагированы (`EventJournalStore`, `VariableHistoryQueryStore`).

Операторам нужны:

1. **Single-DB** — metadata + events + history в одной СУБД (`store=jdbc`).
2. **Certification matrix** — PostgreSQL (prod), PG-совместимые, H2 (edge), enterprise (MSSQL, MySQL, Oracle) по мере готовности migration packs.
3. **External JDBC Data Sources** — объекты `data-source-v1` как коннекторы к **внешним** БД для отчётов, SQL bindings и миграций приложений (не заменяют metadata DB).

Ранее ADR-0015/0016/0025 фиксировали «PostgreSQL для relational core» — этот ADR **не отменяет** PostgreSQL как primary certified prod engine, а вводит **SPI и дорожную карту** переносимости.

## Решение

### 1. RelationalDialect SPI

Пакет `com.ispf.server.relational`:

- `RelationalDialect` — quoting, schema switch, `SKIP LOCKED`, bucket aggregation SQL, Flyway locations, Timescale support flag.
- `RelationalDialectDetector` — `ISPF_METADATA_DB_KIND` или auto-detect по JDBC URL.
- Реализации: `PostgreSqlDialect`, `H2Dialect`, `MssqlDialect`, `MysqlDialect`, `OracleDialect` (enterprise — по мере сертификации).

### 2. Flyway migration packs

```
db/migration/postgresql/   — V1__baseline.sql (greenfield; incremental V1–V70 squashed)
db/migration/h2/           — overrides для edge (при необходимости; по умолчанию H2 использует postgresql pack в MODE=PostgreSQL)
db/migration/mssql/        — enterprise POC: один greenfield baseline
db/migration/mysql/
db/migration/oracle/
```

`FlywayConfigurationCustomizer` выбирает `locations` по активному dialect.

#### Greenfield policy (до v1.0)

**Не поддерживаем** перенос relational core между движками (PG → MSSQL и т.д.) и **не поддерживаем** Flyway upgrade с legacy incremental migrations (V1–V70 удалены).

| Сценарий | Действие |
|----------|----------|
| Новая установка | Пустая БД + `V1__baseline.sql` активного dialect |
| Смена `ISPF_METADATA_DB_KIND` | Новая пустая БД/схема с нуля; конфигурация — через **бандлы** (`package import`) |
| Обновление версии ISPF на том же PostgreSQL | Тот же `V1__baseline.sql`; schema уже применена — Flyway no-op. DDL-изменения — через новые baseline-ревизии или ручной maintenance |
| Legacy DB с историей V1–V70 | Однократно `deploy/vps-flyway-repair.sh` — squash history до V1 + repair checksum |

Конфигурация решения (объекты, приложения, мимики) живёт в **export/import бандлов**, не в «переносе» строк metadata между СУБД.

### 3. Cluster policy

Все реплики кластера (ADR-0028) используют **одинаковый** `ISPF_METADATA_DB_KIND` и `ISPF_DB_URL`. Смена engine — новая БД + импорт бандлов (planned maintenance), не in-place миграция данных.

### 4. Data Sources: internal vs external

| `connectionMode` | Поля | Назначение |
|------------------|------|------------|
| `internal` (default) | `schemaName` | Схема внутри `ISPF_DB` (как раньше) |
| `external` | `jdbcUrl`, `jdbcDriverClass`, `jdbcUsername`, `jdbcPassword`, `poolSize` | Внешняя БД для SQL bindings / reports / migrations |

`ExternalDataSourceRegistry` — Hikari pool per object; `DataSourceSqlSession` — единая точка выполнения SQL.

### 5. Single-DB preset

Режим по умолчанию: `ISPF_EVENT_JOURNAL_STORE=jdbc`, `ISPF_VARIABLE_HISTORY_STORE=jdbc` — всё в `ISPF_DB`. UI: preset «Одна БД» на вкладке «База данных».

## Certification matrix

| Tier | Engine | Prod | Migrations | Notes |
|------|--------|------|------------|-------|
| 0 | PostgreSQL 16 + Timescale | Yes | `postgresql/V1__baseline.sql` | Primary; реестр ПО |
| 1 | PG-compatible (Yugabyte, Cockroach) | On request | `postgresql/` | Тестировать per vendor |
| 2 | H2 (edge/dev) | Edge only | `postgresql/` + PG compat mode | `application-local.yml` |
| 3 | MS SQL Server | POC | `mssql/` | Первый enterprise target |
| 4 | MySQL, Oracle | Planned | `mysql/`, `oracle/` | После MSSQL POC |

## Последствия

**Плюсы:** единая абстракция; явный single-DB сценарий; external JDBC для интеграций без смешивания с metadata.

**Минусы:** enterprise-движки сертифицируются по одному; cluster требует одинаковый engine на всех репликах; смена СУБД — greenfield + бандлы, не live migration.

**Код:** `com.ispf.server.relational.*`, `DataSourceSqlSession`, `ExternalDataSourceRegistry`, расширение `data-source-v1`, UI DataSourceEditor + Database settings.

## Связанные материалы

- [STORAGE_PORTABILITY_INVENTORY.md](../STORAGE_PORTABILITY_INVENTORY.md)
- [DEPLOYMENT.md](../DEPLOYMENT.md) § Storage deployment modes
- [ADR-0028](0028-horizontal-active-active-cluster.md), [ADR-0016](0016-clickhouse-event-journal.md)
- [BLUEPRINTS.md](../BLUEPRINTS.md) — `data-source-v1`
