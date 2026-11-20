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
| Leader locks | `PlatformLeaderLockService` | Table leases (`platform_leader_locks`) — not `pg_advisory_lock` | Keep table leases on MSSQL ([0078](decisions/0078-mssql-metadata-certification.md)) |
| Audit / script locks | `AuditChainStore`, function `advisoryLock` | `pg_advisory_xact_lock` (+ SQLSTATE `23505`) | Dialect / table lock for B1 ([0078](decisions/0078-mssql-metadata-certification.md)) |
| JDBC `LIMIT` stores | Audit/config stores + event journal | Dialect `limitClause()` for audit/config and journal | Lab smoke on MSSQL Evaluation; B2 Prod Yes not signed — see [0078](decisions/0078-mssql-metadata-certification.md) |

## JDBC stores (raw SQL)

~30 `*Store.java` under `packages/ispf-server/src/main/java/com/ispf/server/` — most use ANSI SQL; PG-specific parts in column types and locking. Consolidation via `RelationalDialect` as engines are certified.

## Configuration

| Env | Purpose |
|-----|------------|
| `ISPF_DB_URL` | JDBC URL metadata + jdbc time-series |
| `ISPF_METADATA_DB_KIND` | `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (optional, auto-detect if empty). **`mssql` — Prod Yes (B1)** on SQL Server 2022 Enterprise Evaluation `16.0.4325.4` ([0078](decisions/0078-mssql-metadata-certification.md), [checklist](mssql-sku-promotion-checklist.md)); B2 and A-A not certified. **`mysql` / `oracle` — boot refused** until their gates ([0079](decisions/0079-mysql-metadata-certification.md), [0080](decisions/0080-oracle-metadata-certification.md)) |
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
