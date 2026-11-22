# MySQL B1 SKU promotion checklist (ADR-0079)

> **Language:** Canonical English.  
> **Status:** Operator checklist — **not** a pass by itself. The cell below is **Lab green**, not Prod Yes. Testcontainers and a Docker stand are lab signals only.

Promote `ISPF_METADATA_DB_KIND=mysql` to a production claim only after signing **one** named cell (do not batch Community, Enterprise, and managed services into one signature).

## Lab-green cell (signed 2026-11-22 — not a Prod Yes signature)

| Field | Value |
|-------|-------|
| Product | MySQL **8.4.13** LTS — `MySQL Community Server - GPL`; image `mysql:8.4` (local AMD64 Docker) |
| JDBC client | MariaDB Connector/J 3.5.10 (LGPL-2.1-or-later), `org.mariadb.jdbc.Driver`, `jdbc:mysql://…/ispf?permitMysqlScheme=true` |
| Isolation | `transaction_isolation=READ-COMMITTED` (server flag) |
| Charset / collation | Database `ispf` and every table on `utf8mb4` / `utf8mb4_0900_bin` |
| Dedicated database | Yes (`ispf`); ISPF user has `ALL` on `ispf` only |
| Hibernate | `MySQLDialect` (pinned); `ddl-auto=none` (`local` profile) |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**) |

## Gate evidence

1. Empty DB → Flyway `mysql/V1` → boot + health + login — **PASS** (stand `:18090`; CI `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest`)
2. Cert bundle import (`mysql-b1-cert`) + `by-path` resolves — **PASS** (4 paths; CI `MysqlLiveBundleImportTckTest`)
3. Two JVMs on one writable primary — second non-clustered JVM **PASS** (`:18091` health UP, shared `ispf`). Live lease reclaim is not asserted with cluster off; lease failover stays on CI (`MysqlFlywayBaselineTckTest`)
4. Audit chain — serial re-import **PASS** (`audit_event_chain` rows > 0). Concurrent append (8 writers) + hash verify is CI (`MysqlFlywayBaselineTckTest`)
5. Boot refuse when row isolation is on — **PASS**
6. `HARD` isolation refused; app schema switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`) — **PASS**
7. Boot refuse for JDBC history / dual-write / JDBC journal / cluster on MySQL — **PASS**
8. MariaDB server refused with `ISPF_METADATA_DB_KIND=mysql` and with auto-detect (`mariadb:11.4`) — **PASS**

Evidence: [`tools/mysql-b1-lab/gate-evidence-lab-community.json`](../../tools/mysql-b1-lab/gate-evidence-lab-community.json) — 17 PASS / 0 FAIL.

## Before any Prod Yes signature

| Item | State |
|------|-------|
| Named host | A self-managed MySQL 8.4.x host the operator runs (not Testcontainers, not the lab Docker stand) |
| Hibernate validate | `ddl-auto=validate` fails on this cell (MariaDB Connector/J reports no identifier case for `lower_case_table_names=0`; Hibernate looks tables up in upper case). Fix or waive in writing; until then non-`local` deployments set `SPRING_JPA_HIBERNATE_DDL_AUTO=none` |
| TLS | Replace `allowPublicKeyRetrieval=true` with TLS (`sslMode=verify-full`) |
| Uncovered SQL | PostgreSQL-only SQL on paths this gate does not exercise (platform jobs `NULLS LAST` / `(UUID) getObject`, SQL-binding `||` triggers) reviewed or fenced |
| Re-run | All eight gates above on the named host |

**Tech debt P-MYSQL-SKU-RERUN:** an owner-extended Prod Yes from this lab cell covers only self-managed MySQL 8.4.x Community. MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and any other non-Community cell each need this checklist re-run before a claim names them. MariaDB is never covered.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | `Mysql*` tests (`-Dispf.test.mysqlDocker=true` for the Docker TCKs) |
| Local MySQL 8.4 stand | [`tools/mysql-b1-lab/stand-local.ps1`](../../tools/mysql-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/mysql-b1-lab/gate-evidence.ps1`](../../tools/mysql-b1-lab/gate-evidence.ps1) |

Signer / date: local Community lab gate / 2026-11-22 (Lab green only)

Related: [0079](decisions/0079-mysql-metadata-certification.md), [mysql-certification-epic](mysql-certification-epic.md), [tools/mysql-b1-lab](../../tools/mysql-b1-lab/README.md).
