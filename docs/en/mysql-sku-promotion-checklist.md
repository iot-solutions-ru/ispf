# MySQL B1 SKU promotion checklist (ADR-0079)

> **Language:** Canonical English.  
> **Status:** **Prod Yes (B1) — signed 2026-11-22** on one named cell — **MySQL Community Server 8.4.x + MariaDB Connector/J only** — by owner extension from the Lab-green cell with `ddl-auto=validate` fixed. No other MySQL cell and no MariaDB server inherits it. TLS and PostgreSQL-only SQL outside the gate are not certified ([Known gaps](#known-gaps-tracked-tech-debt)).

Promote `ISPF_METADATA_DB_KIND=mysql` to a production claim only on a signed named cell (do not batch Community, Enterprise, and managed services into one signature).

## Named cell (Prod Yes B1)

| Field | Value |
|-------|-------|
| Product | **MySQL Community Server 8.4.x** LTS, self-managed. Evidence: 8.4.13 `MySQL Community Server - GPL`, image `mysql:8.4` (local AMD64 Docker) |
| JDBC client | **MariaDB Connector/J only** — 3.5.10 (LGPL-2.1-or-later), `org.mariadb.jdbc.Driver`, `jdbc:mysql://…/ispf?permitMysqlScheme=true`. GPL `mysql-connector-j` is not on any classpath |
| Hibernate | `MysqlHibernateDialect` (pinned `MySQLDialect` subclass); the default `ddl-auto=validate` passes. Leave `hibernate.dialect` unset |
| `lower_case_table_names` | `0` (Linux default) on the evidence cell |
| Isolation | `transaction_isolation=READ-COMMITTED` (server flag) |
| Charset / collation | Database `ispf` and every table on `utf8mb4` / `utf8mb4_0900_bin` |
| Dedicated database | Yes (`ispf`); ISPF user has `ALL` on `ispf` only |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`) |
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
- B2 JDBC history / journal and historian dual-write on MySQL (boot refuses them)
- Active-active (`ISPF_CLUSTER_ENABLED=true`), tenant row isolation, `HARD` schemas, per-app schema switch
- TLS on the `ISPF_DB` connection: operator-required, not certified by this signature (tech debt **P-MYSQL-TLS**)
- SQL parity with PostgreSQL: paths the gate does not exercise can still carry PostgreSQL-only SQL (tech debt **P-MYSQL-PG-SQL**)

## Known gaps (tracked tech debt)

Both were recorded at signing as non-claims. Neither was verified on MySQL.

**TLS — P-MYSQL-TLS.** The evidence cell connects without TLS and sets `allowPublicKeyRetrieval=true` so `caching_sha2_password` works over plain TCP. That setting is lab-only: do not carry it to production. A production host must run the `ISPF_DB` connection over TLS (MariaDB Connector/J `sslMode=verify-full`). TLS is the operator's responsibility, and no gate has run over it. To close: re-run the gate over TLS, recommended on the first named host.

**PostgreSQL-only SQL — P-MYSQL-PG-SQL.** No full SQL parity with PostgreSQL is claimed. The gate exercises boot, Flyway, bundle import, `by-path`, audit, leases and the boot fences, not every JDBC path, and the remaining SQL has not been reviewed in full. Known paths, read from the code and not run on MySQL:

| Path | Construct | Expected on MySQL |
|------|-----------|-------------------|
| Report runs through the platform job queue (`POST /api/v1/reports/by-path/run-async`, `GET /api/v1/platform/jobs/{id}`). The console takes this path whenever the job consumer is on, the default on a single node | `PlatformJobService` casts `getObject` on the `CHAR(36)` `job_id` column to `UUID` | Fails with `ClassCastException`; the job stays `QUEUED` |
| `GET /api/v1/platform/metrics` (console System metrics) | `PlatformJobService.listRunningByHolder`: `ORDER BY started_at NULLS LAST` | Fails: `NULLS LAST` is not MySQL syntax |
| SQL bindings refreshed on function success (`on_function_success`) with a trigger object and a trigger function name, single or comma-separated | `ApplicationSqlBindingStore.listForFunctionSuccess`: `',' \|\| trigger_function_name \|\| ','` | Over-matches: MySQL reads `\|\|` as logical OR under the default `sql_mode`, so the binding can refresh after other functions on the trigger object |

To close: port or fence each path, with a MySQL test per path.

## Gate evidence

1. Empty DB → Flyway `mysql/V1` → boot with `ddl-auto=validate` + health + login — **PASS** (stand `:18090`; CI `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest`)
2. Cert bundle import (`mysql-b1-cert`) + `by-path` resolves — **PASS** (4 paths; CI `MysqlLiveBundleImportTckTest`)
3. Two JVMs on one writable primary — second non-clustered JVM **PASS** (`:18091` health UP, shared `ispf`). Live lease reclaim is not asserted with cluster off; lease failover stays on CI (`MysqlFlywayBaselineTckTest`)
4. Audit chain — serial re-import **PASS** (`audit_event_chain` rows > 0). Concurrent append (8 writers) + hash verify is CI (`MysqlFlywayBaselineTckTest`)
5. Boot refuse when row isolation is on — **PASS**
6. `HARD` isolation refused; app schema switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`) — **PASS**
7. Boot refuse for JDBC history / dual-write / JDBC journal / cluster on MySQL — **PASS**
8. MariaDB server refused with `ISPF_METADATA_DB_KIND=mysql` and with auto-detect (`mariadb:11.4`) — **PASS**

| Run | Evidence | Result |
|-----|----------|--------|
| Lab green (`ddl-auto=none`) | [`gate-evidence-lab-community.json`](../../tools/mysql-b1-lab/gate-evidence-lab-community.json) | 17 PASS / 0 FAIL, 2026-11-22 |
| Re-run, `ddl-auto=validate` on every JVM | [`gate-evidence-prod-yes-community.json`](../../tools/mysql-b1-lab/gate-evidence-prod-yes-community.json) | 17 PASS / 0 FAIL, 2026-11-22 |

## Owner extension (2026-11-22)

The Lab-green Docker cell stands in for a named self-managed host, so Prod Yes (B1) covers self-managed MySQL Community Server 8.4.x with MariaDB Connector/J. This overrides the earlier "not Testcontainers, not the lab Docker stand" rule for this cell only. A gate re-run on the first named host is recommended, not blocking.

## Sign-off items

| Item | State |
|------|-------|
| Hibernate validate | **Closed 2026-11-22.** ISPF pins `MysqlHibernateDialect`, a `MySQLDialect` subclass: it looks names up as written when MariaDB Connector/J reports no identifier case (`lower_case_table_names=0`, the Linux default), and accepts the baseline's `LONGBLOB` for `BINARY` mappings. No operator property is needed. Leave `hibernate.dialect` unset: plain `MySQLDialect` fails validation with "missing table" |
| Named host | **Owner extension** (above) |
| Re-run | **Done** on the Lab-green cell with validate on every JVM (table above) |
| TLS | **Not certified; operator-required.** Recorded at signing as a non-claim, not verified. `allowPublicKeyRetrieval=true` stays a lab-only stand setting. Tech debt **P-MYSQL-TLS** ([Known gaps](#known-gaps-tracked-tech-debt)) |
| Uncovered SQL | **Known non-claim; tracked.** No full SQL parity. PostgreSQL-only SQL on paths this gate does not exercise (platform jobs `NULLS LAST` / `(UUID) getObject`, SQL-binding `\|\|` triggers) is listed, not fixed. Tech debt **P-MYSQL-PG-SQL** ([Known gaps](#known-gaps-tracked-tech-debt)) |

**Tech debt P-MYSQL-SKU-RERUN:** this Prod Yes covers only self-managed MySQL Community Server 8.4.x with MariaDB Connector/J. MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and any other non-Community cell each need this checklist re-run before a claim names them. MariaDB is never covered.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | `Mysql*` tests (`-Dispf.test.mysqlDocker=true` for the Docker TCKs) |
| Local MySQL 8.4 stand | [`tools/mysql-b1-lab/stand-local.ps1`](../../tools/mysql-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/mysql-b1-lab/gate-evidence.ps1`](../../tools/mysql-b1-lab/gate-evidence.ps1) |

Signer / date: owner extension from the local Community lab gate / 2026-11-22 — signed; TLS and PostgreSQL-only SQL recorded as non-claims (**P-MYSQL-TLS**, **P-MYSQL-PG-SQL**)

Related: [0079](decisions/0079-mysql-metadata-certification.md), [mysql-certification-epic](mysql-certification-epic.md), [tools/mysql-b1-lab](../../tools/mysql-b1-lab/README.md).
