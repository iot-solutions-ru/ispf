# Epic: Oracle relational certification (ADR-0080)

> **Language:** Canonical English. Russian: [ru/oracle-certification-epic.md](../ru/oracle-certification-epic.md).  
> **Status:** B1 **Prod Yes** on self-managed Oracle Database Free 23.26.x + operator-supplied `ojdbc17` only (owner override 2026-11-23, [checklist](oracle-sku-promotion-checklist.md)) — delivery board for [ADR-0080](decisions/0080-oracle-metadata-certification.md). `ddl-auto=validate` is not fixed; the cell runs `none`. Other editions and 19c: **P-ORACLE-SKU**, **P-ORACLE-19C**.  
> **Policy:** MySQL B1 Prod Yes was signed on 2026-11-22 ([0079](decisions/0079-mysql-metadata-certification.md)), which unblocked the B1 pack. The Oracle Prod Yes comes from its own gate and names only the Free cell.

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

## Prod Yes (B1) sign — signed 2026-11-23

- [x] Named cell: Oracle Database Free 23.26.x by owner override from the Lab-green cell, 18 PASS / 0 FAIL ([`gate-evidence-lab-free.json`](../../tools/oracle-b1-lab/gate-evidence-lab-free.json)); owner decision in [`gate-evidence-prod-yes-free.json`](../../tools/oracle-b1-lab/gate-evidence-prod-yes-free.json); a named-host re-run is recommended, not blocking
- [x] Hibernate `ddl-auto=validate`: **waived in writing, not fixed**. Every Oracle deployment sets `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (**P-ORACLE-VALIDATE**)
- [x] 19c, `''` writes outside the gate's paths, PostgreSQL-only SQL outside the gate's paths, Oracle Net encryption / TLS: recorded as non-claims with tickets, not verified (**P-ORACLE-19C**, **P-ORACLE-EMPTY**, **P-ORACLE-PG-SQL**, **P-ORACLE-TLS**)
- [x] B1 fences unchanged; ISPF still ships no Oracle JDBC driver

## Tech debt after the sign

- [ ] **P-ORACLE-SKU** — checklist run per named non-Free edition or managed service (Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle)
- [ ] **P-ORACLE-19C** — run the B1 gate on 19c, not only on 23.26 Free
- [ ] **P-ORACLE-VALIDATE** — make Hibernate `ddl-auto=validate` pass on Oracle; until then `none`
- [ ] **P-ORACLE-EMPTY** — audit `''` writes into `NOT NULL` text columns on paths the gate does not exercise
- [ ] **P-ORACLE-PG-SQL** — review the remaining JDBC SQL for PostgreSQL-only constructs (bucket aggregate, `CLOB` compared with `=`) and port or fence what the review finds ([checklist](oracle-sku-promotion-checklist.md#known-gaps-tracked-tech-debt))
- [ ] **P-ORACLE-TLS** — re-run the gate over Oracle Net encryption or TLS with a wallet, recommended on the first named host

## Non-goals

- Bundling the Oracle JDBC driver with ISPF, or any redistribution claim for it
- Bundling Oracle B1 into the MySQL first quote
- Claiming A-A without a dedicated soak
- B2 JDBC historian on Oracle in the first quote
- Live PG→Oracle data migration
