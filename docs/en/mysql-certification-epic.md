# Epic: MySQL relational certification (ADR-0079)

> **Language:** Canonical English. Russian: [ru/mysql-certification-epic.md](../ru/mysql-certification-epic.md).  
> **Status:** Phase 1 lab — delivery board for [ADR-0079](decisions/0079-mysql-metadata-certification.md). **Prod: No.**  
> **Policy:** Lab work unblocked after MSSQL B1 Prod Yes ([0078](decisions/0078-mssql-metadata-certification.md)). MariaDB is **not** this epic. Oracle waits for MySQL B1 Prod Yes ([0080](decisions/0080-oracle-metadata-certification.md)).

## Already landed

- [x] Boot refuse `ISPF_METADATA_DB_KIND=mysql` (`UncertifiedRelationalEngineBootGuard` / `assertEngineCertified` → ADR-0079)
- [x] Schema-switch fail-closed (`MysqlDialect.supportsSessionSchemaSwitch=false`)
- [x] README: pack not certified
- [x] MariaDB config / product-name fail-closed (not aliased to MySQL)
- [x] Dialect honesty: unique violation **1062**, `DATE_ADD` expiry, `1`/`0` booleans, ADR-0079 messages
- [x] `assertMysqlB1Fences` + `MysqlCertificationBootGuard` (no single-engine bypass; refuse still wins first)

## Next train (B1 pack — separate PR)

- [ ] Counsel-approved JDBC driver (no GPL `mysql-connector-j` until signed)
- [ ] Amend ADR-0079 to authorize lab Flyway SQL; `tools/mysql-baseline-generate.py` + `V1__baseline.sql`
- [ ] Lockstep check + CI; named SKU MySQL **8.4 LTS** (or 8.0 lab)
- [ ] Testcontainers TCK: Flyway → boot → cert bundle → lease/audit (after driver)
- [ ] Lift prod ban only after § gate on named SKU

## Non-goals

- MariaDB as certified MySQL
- B2 jdbc historian on MySQL in the first quote
- Live PG→MySQL data migration
- Oracle implementation in this epic
