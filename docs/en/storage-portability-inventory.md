> **Language:** Canonical English. Russian edition: [ru/storage-portability-inventory.md](../ru/storage-portability-inventory.md).

# PostgreSQL dependency inventory (ADR-0037)

Automated and manual audit of PG-specific constructs for relational core portability.

## Flyway migrations (`db/migration/postgresql/`)

| Construct | Examples | Porting |
|-------------|---------|--------------|
| `UUID` | V10, V25, V62 | `UNIQUEIDENTIFIER` (MSSQL), `CHAR(36)` (MySQL) |
| `BYTEA` | V34 | `VARBINARY(MAX)` / `BLOB` |
| `JSONB` | V38, V52 | `NVARCHAR(MAX)` + JSON / native JSON |
| `TIMESTAMPTZ` | many | `DATETIMEOFFSET` / `TIMESTAMP WITH TIME ZONE` |
| `CREATE SCHEMA` | V10 | dialect-specific |
| PG indexes | `USING GIN` | review per engine |

**Volume:** single `V1__baseline.sql` in `postgresql/` (squash legacy V1–V70, greenfield only). Enterprise packs — **one baseline** per engine (see ADR-0037 § Greenfield policy).

## Greenfield and engine switch (ADR-0037)

Before v1.0: new install = new DB; changing `ISPF_METADATA_DB_KIND` = empty DB + Flyway baseline + bundle config import. Metadata migration between PostgreSQL and MSSQL/Oracle is **not** in scope.

## Java — PG-only SQL

| Area | File | Construct | Dialect method |
|---------|------|-------------|----------------|
| Job queue | `PlatformJobService` | `FOR UPDATE SKIP LOCKED` | `queuedJobSelectSql()` |
| App schemas | `ApplicationSchemaSession` | `SET search_path` | `activateSchemaSql()` |
| Platform catalog | `PlatformSqlCatalog` | `public.` vs `PUBLIC.` | `platformSchemaPrefix()` |
| Historian buckets | `JdbcVariableHistoryQueryStore` | `extract(epoch...)`, `to_timestamp` | `variableSampleBucketAggregateSql()` |
| Timescale | `TimescaleHypertableInitializer` | `create_hypertable` | `supportsTimescale()` |
| Leader locks | `PlatformLeaderLockService` | PG advisory locks | dialect (future) |

## JDBC stores (raw SQL)

~30 `*Store.java` under `packages/ispf-server/src/main/java/com/ispf/server/` — most use ANSI SQL; PG-specific parts in column types and locking. Consolidation via `RelationalDialect` as engines are certified.

## Configuration

| Env | Purpose |
|-----|------------|
| `ISPF_DB_URL` | JDBC URL metadata + jdbc time-series |
| `ISPF_METADATA_DB_KIND` | `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (optional, auto-detect if empty) |
| `ISPF_EVENT_JOURNAL_STORE` | `jdbc` = same DB |
| `ISPF_VARIABLE_HISTORY_STORE` | `jdbc` = same DB |

## Deployment modes

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

See [clickhouse-prod-playbook](clickhouse-prod-playbook.md).
