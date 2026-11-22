# Epic: MySQL relational certification (ADR-0079)

> **Language:** Canonical English. Russian: [ru/mysql-certification-epic.md](../ru/mysql-certification-epic.md).  
> **Status:** B1 **Lab green** — delivery board for [ADR-0079](decisions/0079-mysql-metadata-certification.md). **Prod: No.**  
> **Policy:** Lab work unblocked after MSSQL B1 Prod Yes ([0078](decisions/0078-mssql-metadata-certification.md)). MariaDB is **not** this epic. Oracle waits for MySQL B1 Prod Yes ([0080](decisions/0080-oracle-metadata-certification.md)).

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

## Next train (Prod Yes — not started)

- [ ] Hibernate `ddl-auto=validate` on MySQL (identifier-case metadata from MariaDB Connector/J)
- [ ] TLS on the ISPF_DB connection instead of `allowPublicKeyRetrieval`
- [ ] Review / fence PostgreSQL-only SQL on paths the gate does not exercise (platform jobs, SQL-binding triggers)
- [ ] Named self-managed host signature; **P-MYSQL-SKU-RERUN** for Enterprise / managed / non-Community cells

## Non-goals

- MariaDB as certified MySQL
- B2 jdbc historian on MySQL in the first quote
- Live PG→MySQL data migration
- Oracle implementation in this epic
