# Epic: Oracle relational certification (ADR-0080)

> **Language:** Canonical English. Russian: [ru/oracle-certification-epic.md](../ru/oracle-certification-epic.md).  
> **Status:** Phase 1 lab — delivery board for [ADR-0080](decisions/0080-oracle-metadata-certification.md). **Prod: No.**  
> **Policy:** Phase 1 started early by owner decision. MySQL B1 Prod Yes was signed on 2026-11-22 ([0079](decisions/0079-mysql-metadata-certification.md)), so the B1 pack may start.

## Already landed

- [x] Boot refuse `ISPF_METADATA_DB_KIND=oracle` (`UncertifiedRelationalEngineBootGuard` / `assertEngineCertified` → ADR-0080)
- [x] Schema-switch fail-closed (`OracleDialect.supportsSessionSchemaSwitch=false`; activate / create messages cite ADR-0080)
- [x] README: pack not certified, no Flyway SQL, no driver
- [x] Dialect honesty: unique violation **ORA-00001** only, `NUMTODSINTERVAL` expiry, `1`/`0` booleans, `SELECT 1 FROM DUAL` reset, identifiers with `"` or NUL fail closed
- [x] `assertOracleB1Fences` + `OracleCertificationBootGuard` (no single-engine bypass; the engine refuse still wins first, pre-Flyway and on context refresh)

## Next train (B1 pack — separate PR, unblocked by MySQL B1 Prod Yes)

- [ ] Counsel-approved Oracle JDBC driver (no `ojdbc*` on any classpath until signed)
- [ ] Amend ADR-0080 to authorize lab Flyway SQL; generator + `db/migration/oracle/V1__baseline.sql` + lockstep CI
- [ ] Named SKU (19c or 23ai, named edition); identifier length and NULL / empty-string rules on that SKU
- [ ] Oracle SQL for the inherited helpers: `limitClause`, `selectTopOneForUpdate`, lock-row insert (`SELECT` without `FROM`), job claim (row-limiting clause with `FOR UPDATE`)
- [ ] Runtime-settings hot-reload guard for the JDBC event journal (mirror MSSQL / MySQL B1)
- [ ] Testcontainers TCK: Flyway → boot → cert bundle → lease/audit (Oracle Database Free = lab evidence only)
- [ ] Lift prod ban only after named-SKU gate

## Non-goals

- Bundling Oracle B1 into the MySQL first quote
- Claiming A-A without a dedicated soak
- B2 JDBC historian on Oracle in the first quote
- Live PG→Oracle data migration
