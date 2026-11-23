# MySQL B1 SKU promotion checklist (ADR-0079)

> **Language:** Canonical English.  
> **Status:** **Prod Yes (B1) — signed 2026-11-22** on one named cell — **MySQL Community Server 8.4.x + MariaDB Connector/J only** — by owner extension from the Lab-green cell with `ddl-auto=validate` fixed. No other MySQL cell and no MariaDB server inherits it. TLS is not certified, and SQL outside the paths the gate and the MySQL TCKs exercise is not claimed ([Known gaps](#known-gaps-tracked-tech-debt)).

Promote `ISPF_METADATA_DB_KIND=mysql` to a production claim only on a signed named cell (do not batch Community, Enterprise, and managed services into one signature).

## Named cell (Prod Yes B1)

| Field | Value |
|-------|-------|
| Product | **MySQL Community Server 8.4.x** LTS, self-managed. Evidence: 8.4.13 `MySQL Community Server - GPL`, image `mysql:8.4` (local AMD64 Docker) |
| JDBC client | **MariaDB Connector/J only** — 3.5.11 (LGPL-2.1-or-later; gate evidence: 3.5.10), `org.mariadb.jdbc.Driver`, `jdbc:mysql://…/ispf?permitMysqlScheme=true`. GPL `mysql-connector-j` is not on any classpath |
| Hibernate | `MysqlHibernateDialect` (pinned `MySQLDialect` subclass); the default `ddl-auto=validate` passes. Leave `hibernate.dialect` unset |
| `lower_case_table_names` | `0` (Linux default) on the evidence cell, read by the gate (`g0_engine`) |
| Isolation | `transaction_isolation=READ-COMMITTED` (server flag) |
| Charset / collation | Database `ispf` and every table on `utf8mb4` / `utf8mb4_0900_bin` |
| Dedicated database | Yes (`ispf`); ISPF user has `ALL` on `ispf` only |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`). JDBC history / journal on this cell is the opt-in single-engine profile, signed separately: [B2 checklist](mysql-b2-sku-promotion-checklist.md) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**) |
| TLS | Not used on the evidence cell (`allowPublicKeyRetrieval=true`, lab-only). Operator-required in production; **not certified** (**P-MYSQL-TLS**) |

## What Prod Yes (B1) means on this cell

- Platform metadata (`ISPF_DB`) on a dedicated MySQL Community Server 8.4.x database, reached through MariaDB Connector/J, under the B1 boot fences (`MetadataDialectBootPolicy.assertMysqlB1Fences`)
- Greenfield Flyway `mysql/V1`, then configuration by bundle import; no in-place engine migration
- Hibernate schema validation on, as in the default profile

## Explicit non-claims

- MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and every other non-Community cell (tech debt **P-MYSQL-SKU-RERUN**)
- MySQL 8.0 and 9.x Innovation releases
- MariaDB Server under any `ISPF_METADATA_DB_KIND` (boot refuses it), and any JDBC client other than MariaDB Connector/J
- B2 JDBC history / journal under this B1 signature: boot refuses them without `ISPF_MYSQL_SINGLE_ENGINE=true`; with the flag they are **Prod Yes (B2)** on this cell ([B2 checklist](mysql-b2-sku-promotion-checklist.md)). Historian dual-write stays refused either way
- Active-active (`ISPF_CLUSTER_ENABLED=true`), tenant row isolation, `HARD` schemas, per-app schema switch
- TLS on the `ISPF_DB` connection: operator-required, not certified by this signature (tech debt **P-MYSQL-TLS**)
- Full SQL parity with PostgreSQL: SQL outside the paths the gate and the MySQL TCKs exercise has not been reviewed in full (tech debt **P-MYSQL-PG-SQL**)

## Known gaps (tracked tech debt)

TLS was recorded at signing as a non-claim and has not been verified on MySQL. The PostgreSQL-only SQL found at signing is fixed and runs on MySQL; full SQL parity stays a non-claim.

**TLS — P-MYSQL-TLS.** The evidence cell connects without TLS and sets `allowPublicKeyRetrieval=true` so `caching_sha2_password` works over plain TCP. That setting is lab-only: do not carry it to production. A production host must run the `ISPF_DB` connection over TLS (MariaDB Connector/J `sslMode=verify-full`). TLS is the operator's responsibility, and no gate has run over it. To close: re-run the gate over TLS, recommended on the first named host.

**PostgreSQL-only SQL — P-MYSQL-PG-SQL.** No full SQL parity with PostgreSQL is claimed. The gate exercises boot, Flyway, bundle import, `by-path`, audit, leases, platform metrics, the job queue and the boot fences, not every JDBC path, and the remaining SQL has not been reviewed in full. The three PostgreSQL-only constructs found at signing are fixed through `RelationalDialect` and run on MySQL 8.4. With the old SQL put back, each TCK named in its row fails on MySQL:

| Path | Before | Now | MySQL evidence |
|------|--------|-----|----------------|
| Report runs through the platform job queue (`POST /api/v1/reports/by-path/run-async`, `GET /api/v1/platform/jobs/{id}`). The console takes this path whenever the job consumer is on, the default on a single node | `PlatformJobService` cast `getObject` on the `CHAR(36)` `job_id` column to `UUID`: `ClassCastException`, the job stayed `QUEUED` | `job_id` is read as a string | `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest` (job worker), gate `g9_job_queue` |
| `GET /api/v1/platform/metrics` (console System metrics) | `PlatformJobService.listRunningByHolder`: `ORDER BY started_at NULLS LAST`, a syntax error on MySQL | `RelationalDialect.orderByNullsLast`: `started_at IS NULL, started_at` | `MysqlFlywayBaselineTckTest` (order asserted), `MysqlLiveBundleImportTckTest`, gate `g9_metrics` |
| SQL bindings refreshed on function success (`on_function_success`) with a trigger object and a trigger function name, single or comma-separated | `ApplicationSqlBindingStore.listForFunctionSuccess`: `',' \|\| trigger_function_name \|\| ','`. MySQL reads `\|\|` as logical OR under the default `sql_mode`, so bindings also refreshed after other functions on the trigger object | `RelationalDialect.concatExpression`: `CONCAT(',', trigger_function_name, ',')` | `MysqlFlywayBaselineTckTest` (single name, list, non-matching function) |

To close: review the remaining JDBC SQL for PostgreSQL-only constructs, and port or fence what the review finds, with a MySQL test per path.

## Gate evidence

1. Empty DB → Flyway `mysql/V1` → boot with `ddl-auto=validate` + health + login — **PASS** (stand `:18090`; the gate reads `lower_case_table_names` and checks `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` on the stand JVM with `docker inspect`; CI `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest`)
2. Cert bundle import (`mysql-b1-cert`) + `by-path` resolves — **PASS** (4 paths; CI `MysqlLiveBundleImportTckTest`)
3. Two JVMs on one writable primary — second non-clustered JVM **PASS** (`:18091` health UP, shared `ispf`). Live lease reclaim is not asserted with cluster off; lease failover stays on CI (`MysqlFlywayBaselineTckTest`)
4. Audit chain — serial re-import **PASS** (`audit_event_chain` rows > 0). Concurrent append (8 writers) + hash verify is CI (`MysqlFlywayBaselineTckTest`)
5. Boot refuse when row isolation is on — **PASS**
6. `HARD` isolation refused; app schema switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`) — **PASS**
7. Boot refuse for JDBC history / dual-write / JDBC journal / cluster on MySQL — **PASS**
8. MariaDB server refused with `ISPF_METADATA_DB_KIND=mysql` and with auto-detect (`mariadb:11.4`) — **PASS**
9. `GET /api/v1/platform/metrics` 200, and a queued report run goes `QUEUED` → `COMPLETED` through the job worker — **PASS** (CI `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest`)

The JSON files below are the collector's output, not edited by hand. The owner decision is recorded apart from them in [`gate-evidence-prod-yes-community.json`](../../tools/mysql-b1-lab/gate-evidence-prod-yes-community.json).

| Run | Evidence | Result |
|-----|----------|--------|
| Lab green (`ddl-auto=none`) | [`gate-evidence-lab-community.json`](../../tools/mysql-b1-lab/gate-evidence-lab-community.json) | 17 PASS / 0 FAIL, 2026-11-22 |
| Re-run: `ddl-auto=validate` checked on the stand JVM and the second JVM, `lower_case_table_names` read, item 9 added | [`gate-evidence-validate-community.json`](../../tools/mysql-b1-lab/gate-evidence-validate-community.json) | 20 PASS / 0 FAIL, 2026-11-22 |

## Owner extension (2026-11-22)

The Lab-green Docker cell stands in for a named self-managed host, so Prod Yes (B1) covers self-managed MySQL Community Server 8.4.x with MariaDB Connector/J. This overrides the earlier "not Testcontainers, not the lab Docker stand" rule for this cell only. A gate re-run on the first named host is recommended, not blocking.

## Sign-off items

| Item | State |
|------|-------|
| Hibernate validate | **Closed 2026-11-22.** ISPF pins `MysqlHibernateDialect`, a `MySQLDialect` subclass: it looks names up as written when MariaDB Connector/J reports no identifier case (`lower_case_table_names=0`, the Linux default), and accepts the baseline's `LONGBLOB` for `BINARY` mappings. No operator property is needed. Leave `hibernate.dialect` unset: plain `MySQLDialect` fails validation with "missing table" |
| Named host | **Owner extension** (above) |
| Re-run | **Done** on the Lab-green cell with validate on the stand JVM and the second JVM (table above) |
| TLS | **Not certified; operator-required.** Recorded at signing as a non-claim, not verified. `allowPublicKeyRetrieval=true` stays a lab-only stand setting. Tech debt **P-MYSQL-TLS** ([Known gaps](#known-gaps-tracked-tech-debt)) |
| Uncovered SQL | **Fixed paths; parity not claimed.** The PostgreSQL-only SQL found at signing (platform jobs `(UUID) getObject` / `NULLS LAST`, SQL-binding `\|\|` triggers) is fixed and runs on MySQL in CI and in the gate. No full SQL parity: the remaining SQL is not reviewed in full. Tech debt **P-MYSQL-PG-SQL** ([Known gaps](#known-gaps-tracked-tech-debt)) |

**Tech debt P-MYSQL-SKU-RERUN:** this Prod Yes covers only self-managed MySQL Community Server 8.4.x with MariaDB Connector/J. MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and any other non-Community cell each need this checklist re-run before a claim names them. MariaDB is never covered.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | `Mysql*` tests (`-Dispf.test.mysqlDocker=true` for the Docker TCKs) |
| Local MySQL 8.4 stand | [`tools/mysql-b1-lab/stand-local.ps1`](../../tools/mysql-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/mysql-b1-lab/gate-evidence.ps1`](../../tools/mysql-b1-lab/gate-evidence.ps1) |

Signer / date: owner extension from the local Community lab gate / 2026-11-22 — signed; TLS and full SQL parity recorded as non-claims (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**)

Related: [0079](decisions/0079-mysql-metadata-certification.md), [mysql-certification-epic](mysql-certification-epic.md), [tools/mysql-b1-lab](../../tools/mysql-b1-lab/README.md).
