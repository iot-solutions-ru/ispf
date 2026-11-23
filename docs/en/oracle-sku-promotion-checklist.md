# Oracle B1 SKU promotion checklist (ADR-0080)

> **Language:** Canonical English.  
> **Status:** **Prod Yes (B1) — signed 2026-11-23** on one named cell — **self-managed Oracle Database Free 23.26.x + operator-supplied `ojdbc17` only** — by owner override from the Lab-green cell. Hibernate `ddl-auto=validate` is **not** fixed: the cell runs `SPRING_JPA_HIBERNATE_DDL_AUTO=none`. No other Oracle edition, release, or managed service inherits this signature. 19c, `''` writes, SQL outside the gate's paths, and TLS are not claimed ([Known gaps](#known-gaps-tracked-tech-debt)).

Promote `ISPF_METADATA_DB_KIND=oracle` to a production claim only on a signed named cell (do not batch Free, Standard Edition 2, Enterprise Edition, and managed services into one signature).

## Adding the Oracle JDBC driver

ISPF does not ship an Oracle JDBC driver ([ADR-0080 § Amendment 2](decisions/0080-oracle-metadata-certification.md#amendment-2-2026-11-22--driver-on-the-operators-classpath)). Obtain the thin driver yourself under Oracle's license terms: `ojdbc17.jar` (JDK 17+; ISPF runs on JDK 25), for example `com.oracle.database.jdbc:ojdbc17` from Maven Central. Then start ISPF through Spring Boot's `PropertiesLauncher`, which the ISPF JAR already contains, with the driver directory on `loader.path`:

```bash
mkdir -p lib && cp /path/to/ojdbc17.jar lib/
export ISPF_METADATA_DB_KIND=oracle
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@//db-host:1521/ISPF_SERVICE'
export SPRING_DATASOURCE_USERNAME=ispf SPRING_DATASOURCE_PASSWORD='…'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver
export SPRING_JPA_HIBERNATE_DDL_AUTO=none
java -Dloader.path=lib -cp ispf-server.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Container image: mount the JAR and replace the entrypoint, which otherwise runs `java -jar`:

```bash
docker run -v /path/to/ojdbc17.jar:/opt/ispf/lib/ojdbc17.jar:ro \
  -e ISPF_METADATA_DB_KIND=oracle -e SPRING_DATASOURCE_URL=… -e SPRING_DATASOURCE_USERNAME=… \
  -e SPRING_DATASOURCE_PASSWORD=… -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=none \
  --entrypoint sh <ispf-image> \
  -c 'exec java $JAVA_OPTS -Dloader.path=/opt/ispf/lib -cp /opt/ispf/ispf-server.jar org.springframework.boot.loader.launch.PropertiesLauncher'
```

Without the driver, boot stops before the DataSource is built: `ISPF_METADATA_DB_KIND=oracle needs the Oracle JDBC thin driver (oracle.jdbc.OracleDriver, for example ojdbc17.jar) on the classpath. ISPF does not ship it (ADR-0080). …`. `SPRING_JPA_HIBERNATE_DDL_AUTO=none` is part of the signed cell: the default profile's `validate` has not run on Oracle. Also set the B1 fences below; boot refuses each unsupported mode.

## Named cell (Prod Yes B1)

| Field | Value |
|-------|-------|
| Product | **Oracle Database Free** — Oracle AI Database 26ai Free, release **23.26.x** (the 23ai long-term line), self-managed. Evidence: 23.26.4.0.0, image `gvenzl/oracle-free:23-slim-faststart` (local AMD64 Docker) |
| JDBC client | Oracle JDBC thin **`ojdbc17`, operator-supplied**, on `loader.path` (`PropertiesLauncher`). ISPF does not ship it; it is not inside `bootJar`. Evidence: 23.26.3.0.0 |
| Character set / strings | `AL32UTF8`, `MAX_STRING_SIZE=STANDARD` (evidence: 8 KiB blocks); DDL uses `CHAR` length semantics |
| Schema | Dedicated schema user in PDB `FREEPDB1` (evidence: `ispf`); an Oracle schema is a user |
| Hibernate | `OracleDialect` (auto-detected); `Instant` bound as `TIMESTAMP`. **`SPRING_JPA_HIBERNATE_DDL_AUTO=none` is required**: the default profile sets `validate`, which has not run on Oracle (**P-ORACLE-VALIDATE**). The evidence cell ran the `local` profile, which sets `none` |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**) |
| TLS | Not used on the evidence cell (no Oracle Net encryption). Operator-required in production; **not certified** (**P-ORACLE-TLS**) |

## What Prod Yes (B1) means on this cell

- Platform metadata (`ISPF_DB`) in a dedicated schema on a self-managed Oracle Database Free 23.26.x, reached through an operator-supplied `ojdbc17`, under the B1 boot fences (`MetadataDialectBootPolicy.assertOracleB1Fences`)
- Greenfield Flyway `oracle/V1`, then configuration by bundle import; no in-place engine migration
- Hibernate schema validation off (`ddl-auto=none`); the Flyway pack defines the schema
- Oracle Database Free's own resource caps (CPU, memory, user data) apply. This signature does not lift them, and nothing past them was measured

Sales line: **Oracle Database Free 23.26 B1 metadata, operator-supplied ojdbc17.**

## Explicit non-claims

- Oracle Database Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle, and every other non-Free edition or managed service (tech debt **P-ORACLE-SKU**)
- Oracle Database 19c (tech debt **P-ORACLE-19C**)
- Hibernate `ddl-auto=validate` on Oracle: not fixed and not run; the cell runs `none` (tech debt **P-ORACLE-VALIDATE**)
- Load past Oracle Database Free's resource caps
- B2 JDBC history / journal and historian dual-write on Oracle (boot refuses them)
- Active-active (`ISPF_CLUSTER_ENABLED=true`), tenant row isolation, `HARD` schemas, per-app schema switch
- Shipping or redistributing the Oracle JDBC driver: ISPF ships no `ojdbc*`
- PostgreSQL → Oracle data migration: an engine change is a new empty schema plus bundle import
- `''` kept as text on paths the gate does not exercise (tech debt **P-ORACLE-EMPTY**)
- Full SQL parity with PostgreSQL outside the paths the gate and the Oracle TCKs exercise (tech debt **P-ORACLE-PG-SQL**)
- Oracle Net encryption / TLS on the `ISPF_DB` connection: operator-required, not certified by this signature (tech debt **P-ORACLE-TLS**)

## Known gaps (tracked tech debt)

The rows that were open before signing are waived in writing as non-claims ([Sign-off items](#sign-off-items)). None of them was fixed or verified for this signature.

**Edition — P-ORACLE-SKU.** The owner override names Oracle Database Free 23.26.x as the cell. Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle, and any other edition or managed service each need this checklist run before a claim names them.

**19c — P-ORACLE-19C.** The DDL and dialect SQL follow 19c rules (no `BOOLEAN`, `FROM DUAL`, no row limit with `FOR UPDATE`) but have run only on 23.26 Free. To close: run the B1 gate on 19c.

**Hibernate validate — P-ORACLE-VALIDATE.** `ddl-auto=validate` has not run on Oracle (`NUMBER(1)` columns against Hibernate's 23ai `BOOLEAN` mapping). It is waived, not fixed: every Oracle deployment sets `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (the `local` profile already does). To close: make validate pass on Oracle, with a test.

**Empty strings — P-ORACLE-EMPTY.** Oracle stores `''` as `NULL`. A `''` write into a `NOT NULL` text column fails with ORA-01400, a loud failure; a nullable column stores `NULL`. The gate exercises boot, Flyway, bundle import, `by-path`, audit, leases, the job claim and the boot fences; other paths have not been audited for `''` writes. To close: audit `''` writes on the remaining paths.

**PostgreSQL-only SQL — P-ORACLE-PG-SQL.** No full SQL parity with PostgreSQL is claimed. The job queue (`job_id` read, running jobs ordered NULLs last) and function-success SQL-binding triggers run on Oracle in the opt-in Docker TCK (`PlatformSqlPathsTck` inside `OracleFlywayBaselineTckTest`). Known PostgreSQL-only SQL remains on paths the gate does not exercise: the bucket aggregate, and `CLOB` columns compared with `=` (ORA-22848). `concatExpression` on Oracle is `||`, where a NULL operand counts as empty text: new callers guard NULL operands, as the SQL-binding trigger query does. To close: review the remaining JDBC SQL, and port or fence what the review finds, with an Oracle test per path.

**TLS — P-ORACLE-TLS.** The evidence cell connects without Oracle Net encryption or TLS. A production host configures Oracle Net encryption, or TLS with a wallet. That is the operator's responsibility, and no gate has run over it. To close: re-run the gate over encryption, recommended on the first named host.

## Gate evidence

1. Empty schema → Flyway `oracle/V1` → boot + health + login — **PASS** (stand `:18092`; Docker TCKs `OracleFlywayBaselineTckTest`, `OracleLiveBundleImportTckTest`)
2. Hibernate `OracleDialect`; no ojdbc inside `bootJar`, driver loaded from `loader.path`; boot without the driver refused with the remedy — **PASS**
3. Cert bundle import (`oracle-b1-cert`) + `by-path` resolves — **PASS** (4 paths; the live Docker TCK imports twice and checks one deployment row)
4. Two JVMs on one schema — second non-clustered JVM **PASS** (`:18093` health UP). Live lease reclaim is not asserted with cluster off; lease failover is in the Docker TCK (`OracleFlywayBaselineTckTest`)
5. Audit chain — serial re-import **PASS** (`audit_event_chain` rows > 0). Concurrent append (8 writers) + hash verify is in the Docker TCK (`OracleFlywayBaselineTckTest`)
6. Job claim with `SKIP LOCKED` through `PlatformJobService` (two concurrent claimers get different jobs) — **PASS** (Docker TCK)
7. Boot refuse for row isolation, `HARD`, JDBC history, dual-write, JDBC journal, cluster on Oracle — **PASS**; app schema switch fail-closed (`OracleDialect.supportsSessionSchemaSwitch=false`)
8. Runtime-settings `PATCH` cannot hot-enable the JDBC event journal — **PASS**

The Docker TCKs are tagged `oracle-docker` and run only with `-Dispf.test.oracleDocker=true` (Docker plus the lab driver); default CI does not run them. Default CI runs the boot-fence unit tests and the Oracle lockstep check.

The JSON below is the collector's output, not edited by hand. The owner decision is recorded apart from it in [`gate-evidence-prod-yes-free.json`](../../tools/oracle-b1-lab/gate-evidence-prod-yes-free.json).

| Run | Evidence | Result |
|-----|----------|--------|
| Lab green (`local` profile, `ddl-auto=none`) | [`gate-evidence-lab-free.json`](../../tools/oracle-b1-lab/gate-evidence-lab-free.json) | 18 PASS / 0 FAIL, 2026-11-22 |

## Owner override (2026-11-23)

Oracle Database Free 23.26.x is the named cell, and the Lab-green Docker cell stands in for a named self-managed Free host. Prod Yes (B1) therefore covers self-managed Oracle Database Free 23.26.x with an operator-supplied `ojdbc17`. For this cell only, the override sets aside two earlier rules: that Oracle Database Free is lab evidence only ([0080](decisions/0080-oracle-metadata-certification.md)), and that a Prod Yes needs a named non-Free edition (this checklist). No other edition, release, or managed service inherits it. A gate re-run on the first named host is recommended, not blocking.

## Sign-off items

| Item | State |
|------|-------|
| Named edition | **Owner override** (above). The cell is Oracle Database Free 23.26.x itself. Standard Edition 2, Enterprise Edition, and managed services are not covered (**P-ORACLE-SKU**) |
| 19c | **Not run; non-claim.** The DDL and dialect SQL follow 19c rules but have only run on 23.26 Free (**P-ORACLE-19C**) |
| Hibernate validate | **Waived in writing, not fixed.** `ddl-auto=validate` has not run on Oracle. Every Oracle deployment sets `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (**P-ORACLE-VALIDATE**) |
| Empty strings | **Non-claim; not audited.** Oracle stores `''` as `NULL`; a `''` write into a `NOT NULL` text column fails with ORA-01400. Paths the gate does not exercise have not been reviewed (**P-ORACLE-EMPTY**) |
| Uncovered SQL | **Non-claim; not reviewed in full.** The bucket aggregate and `CLOB` compared with `=` stay PostgreSQL-only on paths the gate does not exercise. No full SQL parity (**P-ORACLE-PG-SQL**) |
| Network | **Not certified; operator-required.** The evidence cell ran without Oracle Net encryption or TLS. Recorded at signing as a non-claim, not verified (**P-ORACLE-TLS**) |
| Re-run | **Done** on the Lab-green cell, which the owner override names as the signed cell (18 PASS / 0 FAIL). A re-run on the first named host is recommended, not blocking |
| B1 fences | **Unchanged.** Boot still refuses row isolation, `HARD`, JDBC history, dual-write, JDBC journal, and cluster on Oracle |

**Tech debt P-ORACLE-SKU:** this Prod Yes covers only self-managed Oracle Database Free 23.26.x with an operator-supplied `ojdbc17`. Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle, and any other edition or managed service each need this checklist run before a claim names them.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| Boot fences / Docker TCKs | `Oracle*` tests: boot-fence unit tests run in default CI; the `oracle-docker` TCKs run only with `-Dispf.test.oracleDocker=true` (adds the lab driver) |
| Local Oracle Free stand | [`tools/oracle-b1-lab/stand-local.ps1`](../../tools/oracle-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/oracle-b1-lab/gate-evidence.ps1`](../../tools/oracle-b1-lab/gate-evidence.ps1) |

Signer / date: owner override from the local Oracle Database Free lab gate / 2026-11-23 — signed; validate stays `none`, and 19c, `''` writes, SQL outside the gate's paths, and TLS are recorded as non-claims (**P-ORACLE-19C**, **P-ORACLE-VALIDATE**, **P-ORACLE-EMPTY**, **P-ORACLE-PG-SQL**, **P-ORACLE-TLS**)

Related: [0080](decisions/0080-oracle-metadata-certification.md), [oracle-certification-epic](oracle-certification-epic.md), [tools/oracle-b1-lab](../../tools/oracle-b1-lab/README.md).
