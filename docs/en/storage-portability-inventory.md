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
| Job queue | `PlatformJobService` | `FOR UPDATE SKIP LOCKED` | `queuedPlatformJobSelectSql()` |
| Job queue | `PlatformJobService` | `NULLS LAST`; `(UUID) getObject` on `job_id` (a string on MySQL / MSSQL) | `orderByNullsLast()`; `job_id` read as a string |
| SQL bindings | `ApplicationSqlBindingStore` | `\|\|` concat (logical OR on MySQL, none on SQL Server before 2025) | `concatExpression()` |
| App schemas | `ApplicationSchemaSession` | `SET search_path` | `activateSchemaSql()` |
| Platform catalog | `PlatformSqlCatalog` | `public.` vs `PUBLIC.` | `platformSchemaPrefix()` |
| Historian buckets | `JdbcVariableHistoryQueryStore` | `extract(epoch...)`, `to_timestamp` | `variableSampleBucketAggregateSql()` — MSSQL `DATE_BUCKET`; MySQL `TIMESTAMPDIFF` / `TIMESTAMPADD` (single-engine B2, [0079](decisions/0079-mysql-metadata-certification.md)) |
| Journal purge | `JdbcEventJournalStore` | One unbounded `DELETE` | MSSQL `DELETE TOP (5000)`, MySQL `DELETE … LIMIT 5000`, one transaction per batch |
| Timescale | `TimescaleHypertableInitializer` | `create_hypertable` | `supportsTimescale()` |
| Leader locks | `PlatformLeaderLockService` | Table leases (`platform_leader_locks`) — not `pg_advisory_lock` | Keep table leases on MSSQL ([0078](decisions/0078-mssql-metadata-certification.md)) |
| Audit / script locks | `AuditChainStore`, function `advisoryLock` | `pg_advisory_xact_lock` (+ SQLSTATE `23505`) | Dialect / table lock for B1 ([0078](decisions/0078-mssql-metadata-certification.md)); MySQL lock row via `INSERT … ON DUPLICATE KEY UPDATE` ([0079](decisions/0079-mysql-metadata-certification.md)) |
| JDBC `LIMIT` stores | Audit/config stores + event journal | Dialect `limitClause()` for audit/config and journal | Lab smoke on MSSQL Evaluation; B2 Prod Yes signed on Evaluation — see [0078](decisions/0078-mssql-metadata-certification.md), [B2 checklist](mssql-b2-sku-promotion-checklist.md) |

## JDBC stores (raw SQL)

~30 `*Store.java` under `packages/ispf-server/src/main/java/com/ispf/server/` — most use ANSI SQL; PG-specific parts in column types and locking. Consolidation via `RelationalDialect` as engines are certified.

## Configuration

| Env | Purpose |
|-----|------------|
| `ISPF_DB_URL` | JDBC URL metadata + jdbc time-series |
| `ISPF_METADATA_DB_KIND` | `postgresql`, `h2`, `mssql`, `mysql`, `oracle` (optional, auto-detect if empty). **`mssql` — Prod Yes (B1 + B2 + A-A)** on Evaluation + owner-extended licensed Standard / Enterprise / Azure SQL DB\|MI ([0078](decisions/0078-mssql-metadata-certification.md), [A-A](mssql-aa-sku-promotion-checklist.md)). **`mysql` — Prod Yes (B1 + opt-in B2)** on MySQL Community Server 8.4.x with MariaDB Connector/J only; boots only under its fences (B1, or B2 with `ISPF_MYSQL_SINGLE_ENGINE=true`; cluster refused); `jdbc:mysql://…?permitMysqlScheme=true` + `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver` ([0079](decisions/0079-mysql-metadata-certification.md), [B1](mysql-sku-promotion-checklist.md), [B2](mysql-b2-sku-promotion-checklist.md)); other MySQL cells **P-MYSQL-SKU-RERUN**; TLS and SQL outside the gate's paths not certified (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**); **`mariadb` refused** (not MySQL), and a MariaDB server is refused under `mysql` too. **`oracle` — Prod Yes (B1)** on self-managed Oracle Database Free 23.26.x only; boots only with the Flyway pack and an operator-supplied Oracle JDBC driver (`ojdbc17` on `loader.path`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver`; ISPF does not ship it), under B1 fences, with `SPRING_JPA_HIBERNATE_DDL_AUTO=none` ([0080](decisions/0080-oracle-metadata-certification.md), [checklist](oracle-sku-promotion-checklist.md)); other editions, 19c, and managed services **P-ORACLE-SKU** / **P-ORACLE-19C**; validate, `''` writes, SQL outside the gate's paths, and TLS not certified (**P-ORACLE-VALIDATE**, **P-ORACLE-EMPTY**, **P-ORACLE-PG-SQL**, **P-ORACLE-TLS**) |
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
