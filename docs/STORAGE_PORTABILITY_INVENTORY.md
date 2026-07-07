# PostgreSQL dependency inventory (ADR-0037)

Автоматический и ручной аудит PG-специфики для relational core portability.

## Flyway migrations (`db/migration/postgresql/`)

| Конструкция | Примеры | Портирование |
|-------------|---------|--------------|
| `UUID` | V10, V25, V62 | `UNIQUEIDENTIFIER` (MSSQL), `CHAR(36)` (MySQL) |
| `BYTEA` | V34 | `VARBINARY(MAX)` / `BLOB` |
| `JSONB` | V38, V52 | `NVARCHAR(MAX)` + JSON / native JSON |
| `TIMESTAMPTZ` | многие | `DATETIMEOFFSET` / `TIMESTAMP WITH TIME ZONE` |
| `CREATE SCHEMA` | V10 | dialect-specific |
| PG indexes | `USING GIN` | пересмотр per engine |

**Объём:** один `V1__baseline.sql` в `postgresql/` (squash legacy V1–V70, greenfield only). Enterprise packs — **один baseline** на движок (см. ADR-0037 § Greenfield policy).

## Greenfield и смена движка (ADR-0037)

До v1.0: новая установка = новая БД; смена `ISPF_METADATA_DB_KIND` = пустая БД + Flyway baseline + импорт конфигурации бандлами. Миграция metadata между PostgreSQL и MSSQL/Oracle **не** входит в scope.

## Java — PG-only SQL

| Область | Файл | Конструкция | Dialect method |
|---------|------|-------------|----------------|
| Job queue | `PlatformJobService` | `FOR UPDATE SKIP LOCKED` | `queuedJobSelectSql()` |
| App schemas | `ApplicationSchemaSession` | `SET search_path` | `activateSchemaSql()` |
| Platform catalog | `PlatformSqlCatalog` | `public.` vs `PUBLIC.` | `platformSchemaPrefix()` |
| Historian buckets | `JdbcVariableHistoryQueryStore` | `extract(epoch...)`, `to_timestamp` | `variableSampleBucketAggregateSql()` |
| Timescale | `TimescaleHypertableInitializer` | `create_hypertable` | `supportsTimescale()` |
| Leader locks | `PlatformLeaderLockService` | PG advisory locks | dialect (future) |

## JDBC stores (raw SQL)

~30 `*Store.java` under `packages/ispf-server/src/main/java/com/ispf/server/` — большинство используют ANSI SQL; PG-специфика в типах колонок и locking. Консолидация через `RelationalDialect` по мере сертификации движков.

## Конфигурация

| Env | Назначение |
|-----|------------|
| `ISPF_DB_URL` | JDBC URL metadata + jdbc time-series |
| `ISPF_METADATA_DB_KIND` | `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (optional, auto-detect if empty) |
| `ISPF_EVENT_JOURNAL_STORE` | `jdbc` = same DB |
| `ISPF_VARIABLE_HISTORY_STORE` | `jdbc` = same DB |

## Режимы развёртывания

### Single DB (default)

```
ISPF_DB_URL → PostgreSQL
ISPF_EVENT_JOURNAL_STORE=jdbc
ISPF_VARIABLE_HISTORY_STORE=jdbc
```

### Split telemetry

```
ISPF_DB_URL → PostgreSQL (metadata)
ISPF_EVENT_JOURNAL_STORE=clickhouse
ISPF_VARIABLE_HISTORY_STORE=clickhouse
```

См. [CLICKHOUSE_PROD_PLAYBOOK.md](CLICKHOUSE_PROD_PLAYBOOK.md).
