# MySQL B1 SKU promotion checklist (ADR-0079)

> **Language:** Canonical English.  
> **Status:** Operator checklist for **Prod Yes (B1)** on one named cell — **MySQL Community Server 8.4.x + MariaDB Connector/J only** — owner-extended 2026-11-22 from the Lab-green cell with `ddl-auto=validate` fixed. No other MySQL cell and no MariaDB server inherits it.  
> **Draft:** the signature is final only when the open items in [Before signing](#before-signing) close or the owner waives them in writing.

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

## Before signing

| Item | State |
|------|-------|
| Hibernate validate | **Closed 2026-11-22.** ISPF pins `MysqlHibernateDialect`, a `MySQLDialect` subclass: it looks names up as written when MariaDB Connector/J reports no identifier case (`lower_case_table_names=0`, the Linux default), and accepts the baseline's `LONGBLOB` for `BINARY` mappings. No operator property is needed. Leave `hibernate.dialect` unset: plain `MySQLDialect` fails validation with "missing table" |
| Named host | **Owner extension** (above) |
| Re-run | **Done** on the Lab-green cell with validate on every JVM (table above) |
| TLS | **Open.** The lab stand connects with `allowPublicKeyRetrieval=true` and no TLS. A production host uses TLS (`sslMode=verify-full`); no gate has run it yet |
| Uncovered SQL | **Open.** PostgreSQL-only SQL on paths this gate does not exercise (platform jobs `NULLS LAST` / `(UUID) getObject`, SQL-binding `||` triggers): review or fence |

**Tech debt P-MYSQL-SKU-RERUN:** this Prod Yes covers only self-managed MySQL Community Server 8.4.x with MariaDB Connector/J. MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and any other non-Community cell each need this checklist re-run before a claim names them. MariaDB is never covered.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | `Mysql*` tests (`-Dispf.test.mysqlDocker=true` for the Docker TCKs) |
| Local MySQL 8.4 stand | [`tools/mysql-b1-lab/stand-local.ps1`](../../tools/mysql-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/mysql-b1-lab/gate-evidence.ps1`](../../tools/mysql-b1-lab/gate-evidence.ps1) |

Signer / date: owner extension from the local Community lab gate / 2026-11-22 — draft until the open items above close

Related: [0079](decisions/0079-mysql-metadata-certification.md), [mysql-certification-epic](mysql-certification-epic.md), [tools/mysql-b1-lab](../../tools/mysql-b1-lab/README.md).
