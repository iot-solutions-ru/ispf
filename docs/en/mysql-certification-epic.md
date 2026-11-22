# Epic: MySQL relational certification (ADR-0079)

> **Language:** Canonical English. Russian: [ru/mysql-certification-epic.md](../ru/mysql-certification-epic.md).  
> **Status:** B1 **Prod Yes** on MySQL Community Server 8.4.x + MariaDB Connector/J only (owner extension 2026-11-22, [checklist](mysql-sku-promotion-checklist.md)) — delivery board for [ADR-0079](decisions/0079-mysql-metadata-certification.md). Other MySQL cells: **P-MYSQL-SKU-RERUN**.  
> **Policy:** Lab work unblocked after MSSQL B1 Prod Yes ([0078](decisions/0078-mssql-metadata-certification.md)). MariaDB is **not** this epic. MySQL B1 Prod Yes is signed, so the Oracle B1 pack may start ([0080](decisions/0080-oracle-metadata-certification.md)); Oracle stays Prod: No.

## Phase 1 — dialect honesty (landed)

- [x] Schema-switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`)
- [x] MariaDB config / product-name fail-closed (not aliased to MySQL)
- [x] Dialect honesty: unique violation **1062**, `DATE_ADD` expiry, `1`/`0` booleans, ADR-0079 messages
- [x] `assertMysqlB1Fences` + `MysqlCertificationBootGuard` (no single-engine bypass)

## B1 pack + Lab green (landed 2026-11-22)

- [x] JDBC client decided: LGPL MariaDB Connector/J for MySQL servers; no GPL `mysql-connector-j`; MariaDB **server** still refused, including under an explicit `ISPF_METADATA_DB_KIND=mysql`
- [x] ADR-0079 amended to authorize lab Flyway SQL; `tools/mysql-baseline-generate.py` + `mysql/V1__baseline.sql`
- [x] Lockstep check + CI (`tools/mysql-flyway-lockstep-check.py`, [`mysql-flyway-exceptions.txt`](mysql-flyway-exceptions.txt)); named lab SKU MySQL **8.4 LTS** Community
- [x] Testcontainers TCK: Flyway → table/collation smoke → concurrent audit → lease failover (`MysqlFlywayBaselineTckTest`); boot → cert bundle (`MysqlLiveBundleImportTckTest`); tag `mysql-docker`, opt-in `-Dispf.test.mysqlDocker=true`
- [x] Boot path fixes found on the stand: Hibernate `MySQLDialect` pinned (driver name would pick `MariaDBDialect`); catalog check quotes through the dialect; audit/script lock row via `ON DUPLICATE KEY UPDATE` (deadlock 1213 otherwise)
- [x] Hard engine refuse lifted for MySQL only; boot runs under B1 fences; Oracle still refused
- [x] Lab stand + gate: [`tools/mysql-b1-lab`](../../tools/mysql-b1-lab/README.md) — 17 PASS / 0 FAIL on MySQL 8.4.13 Community ([checklist](mysql-sku-promotion-checklist.md))

## Prod Yes (B1) sign — signed 2026-11-22

- [x] Hibernate `ddl-auto=validate` on MySQL — pinned `MysqlHibernateDialect` (identifier case when MariaDB Connector/J reports none; `BINARY` mapping vs `LONGBLOB`); `MysqlLiveBundleImportTckTest` boots with validate on `lower_case_table_names=0`
- [x] TLS on the ISPF_DB connection: recorded as operator-required and **not certified** by this signature; `allowPublicKeyRetrieval` stays a lab-only stand setting (**P-MYSQL-TLS**)
- [x] PostgreSQL-only SQL found at signing (platform jobs, SQL-binding triggers): fixed through `RelationalDialect`, run on MySQL in `MysqlFlywayBaselineTckTest`, `MysqlLiveBundleImportTckTest` and gate items `g9_metrics` / `g9_job_queue`; no full SQL parity is claimed (**P-MYSQL-PG-SQL**)
- [x] Owner extension from the Lab-green cell instead of a named self-managed host: gate re-run with validate on the stand JVM and the second JVM, 20 PASS / 0 FAIL ([`gate-evidence-validate-community.json`](../../tools/mysql-b1-lab/gate-evidence-validate-community.json)); owner decision in [`gate-evidence-prod-yes-community.json`](../../tools/mysql-b1-lab/gate-evidence-prod-yes-community.json); a named-host re-run is recommended, not blocking; **P-MYSQL-SKU-RERUN** for Enterprise / managed / non-Community cells

## Tech debt after the sign

- [ ] **P-MYSQL-TLS** — re-run the gate over TLS (`sslMode=verify-full`), recommended on the first named host
- [ ] **P-MYSQL-PG-SQL** — review the remaining JDBC SQL for PostgreSQL-only constructs and port or fence what the review finds; the paths found at signing are fixed ([checklist](mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt))
- [ ] **P-MYSQL-SKU-RERUN** — checklist re-run per named Enterprise / managed / non-Community cell

## Non-goals

- MariaDB as certified MySQL
- B2 jdbc historian on MySQL in the first quote
- Live PG→MySQL data migration
- Oracle implementation in this epic
