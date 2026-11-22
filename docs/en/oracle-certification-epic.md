# Epic: Oracle relational certification (ADR-0080)

> **Language:** Canonical English. Russian: [ru/oracle-certification-epic.md](../ru/oracle-certification-epic.md).  
> **Status:** **Lab green** — delivery board for [ADR-0080](decisions/0080-oracle-metadata-certification.md). **Prod: No** (Oracle Database Free is not a production edition).  
> **Policy:** MySQL B1 Prod Yes was signed on 2026-11-22 ([0079](decisions/0079-mysql-metadata-certification.md)), which unblocked the B1 pack. An Oracle Prod Yes needs its own named-SKU gate.

## Already landed

- [x] Schema-switch fail-closed (`OracleDialect.supportsSessionSchemaSwitch=false`; activate / create messages cite ADR-0080)
- [x] Dialect honesty: unique violation **ORA-00001** only, `NUMTODSINTERVAL` expiry, `1`/`0` booleans, `SELECT 1 FROM DUAL` reset, identifiers with `"` or NUL fail closed
- [x] `assertOracleB1Fences` + `OracleCertificationBootGuard` (no single-engine bypass)
- [x] ADR-0080 amended: lab Flyway SQL authorized; generator `tools/oracle-baseline-generate.py` + `db/migration/oracle/V1__baseline.sql` + lockstep CI (`tools/oracle-flyway-lockstep-check.py`)
- [x] Driver on the operator's classpath only: no `ojdbc*` in `bootJar` / zip / image; `PropertiesLauncher` + `loader.path` documented; boot refuses with the remedy when the driver is missing; opt-in `oracleLabJdbc` for the Docker gate
- [x] Engine gate admits `oracle` only with the V1 pack and a loadable `oracle.jdbc.OracleDriver`
- [x] 19c-safe SQL for the inherited helpers: `limitClause` (`FETCH FIRST ? ROWS ONLY`), `selectTopOneForUpdate` (`ROWID` + `ROWNUM`), lock-row insert `FROM DUAL`, job claim `FOR UPDATE SKIP LOCKED` without a row limit (one-row fetch)
- [x] ojdbc compatibility: UUID binds as text (`OracleUuidJdbcDataSource`, ORA-17004), `Instant` as `TIMESTAMP` in Hibernate (ORA-18716), quoted `comment` mapping (reserved word)
- [x] Runtime-settings hot-reload guard for the JDBC event journal (as MSSQL / MySQL B1)
- [x] Testcontainers TCK on Oracle Database Free: Flyway → semantics → `SKIP LOCKED` claims → concurrent audit → lease failover; live boot → cert bundle → alarm shelf JPA
- [x] Lab stand + 18/18 gate evidence ([checklist](oracle-sku-promotion-checklist.md))

## Next train (Prod Yes — named SKU)

- [ ] Named edition and host: Enterprise Edition / Standard Edition 2 19c or 23ai/26ai, or one managed service (**P-ORACLE-SKU**)
- [ ] Run the B1 gate on 19c, not only on 23.26 Free
- [ ] Hibernate `ddl-auto=validate` on Oracle, fixed or waived in writing
- [ ] Audit `''` writes into `NOT NULL` text columns on paths the gate does not exercise
- [ ] Oracle Net encryption / TLS on the named host

## Non-goals

- Bundling the Oracle JDBC driver with ISPF, or any redistribution claim for it
- Bundling Oracle B1 into the MySQL first quote
- Claiming A-A without a dedicated soak
- B2 JDBC historian on Oracle in the first quote
- Live PG→Oracle data migration
