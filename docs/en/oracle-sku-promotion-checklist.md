# Oracle B1 SKU promotion checklist (ADR-0080)

> **Language:** Canonical English.  
> **Status:** Operator checklist — **not** a pass by itself. The cell below is **Lab green**, not Prod Yes. Oracle Database Free, Testcontainers, and a Docker stand are lab signals only.

Promote `ISPF_METADATA_DB_KIND=oracle` to a production claim only after signing **one** named cell (do not batch Free, Standard Edition 2, Enterprise Edition, and managed services into one signature).

## Adding the Oracle JDBC driver

ISPF does not ship an Oracle JDBC driver ([ADR-0080 § Amendment 2](decisions/0080-oracle-metadata-certification.md#amendment-2-2026-11-22--driver-on-the-operators-classpath)). Obtain the thin driver yourself under Oracle's license terms: `ojdbc17.jar` (JDK 17+; ISPF runs on JDK 25), for example `com.oracle.database.jdbc:ojdbc17` from Maven Central. Then start ISPF through Spring Boot's `PropertiesLauncher`, which the ISPF JAR already contains, with the driver directory on `loader.path`:

```bash
mkdir -p lib && cp /path/to/ojdbc17.jar lib/
export ISPF_METADATA_DB_KIND=oracle
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@//db-host:1521/ISPF_SERVICE'
export SPRING_DATASOURCE_USERNAME=ispf SPRING_DATASOURCE_PASSWORD='…'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver
java -Dloader.path=lib -cp ispf-server.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Container image: mount the JAR and replace the entrypoint, which otherwise runs `java -jar`:

```bash
docker run -v /path/to/ojdbc17.jar:/opt/ispf/lib/ojdbc17.jar:ro \
  -e ISPF_METADATA_DB_KIND=oracle -e SPRING_DATASOURCE_URL=… -e SPRING_DATASOURCE_USERNAME=… \
  -e SPRING_DATASOURCE_PASSWORD=… -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver \
  --entrypoint sh <ispf-image> \
  -c 'exec java $JAVA_OPTS -Dloader.path=/opt/ispf/lib -cp /opt/ispf/ispf-server.jar org.springframework.boot.loader.launch.PropertiesLauncher'
```

Without the driver, boot stops before the DataSource is built: `ISPF_METADATA_DB_KIND=oracle needs the Oracle JDBC thin driver (oracle.jdbc.OracleDriver, for example ojdbc17.jar) on the classpath. ISPF does not ship it (ADR-0080). …`. Also set the B1 fences below; boot refuses each unsupported mode.

## Lab-green cell (signed 2026-11-22 — not a Prod Yes signature)

| Field | Value |
|-------|-------|
| Product | Oracle AI Database 26ai **Free** Release **23.26.4.0.0** (the 23ai long-term line); image `gvenzl/oracle-free:23-slim-faststart` (local AMD64 Docker) |
| JDBC client | Oracle JDBC thin `ojdbc17` 23.26.3.0.0 on `loader.path` (`PropertiesLauncher`); not inside `bootJar` |
| Character set / strings | `AL32UTF8`, `MAX_STRING_SIZE=STANDARD`, 8 KiB blocks; DDL uses `CHAR` length semantics |
| Schema | Dedicated schema user `ispf` in PDB `FREEPDB1` (an Oracle schema is a user) |
| Hibernate | `OracleDialect` (auto-detected); `Instant` bound as `TIMESTAMP`; `ddl-auto=none` (`local` profile) |
| `ISPF_TENANT_DB_ROW_ISOLATION` | `false` (single-tenant) |
| History / journal | Disabled (`ISPF_VARIABLE_HISTORY_ENABLED=false`, `ISPF_EVENT_JOURNAL_ENABLED=false`) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**) |

## Gate evidence

1. Empty schema → Flyway `oracle/V1` → boot + health + login — **PASS** (stand `:18092`; CI `OracleFlywayBaselineTckTest`, `OracleLiveBundleImportTckTest`)
2. Hibernate `OracleDialect`; no ojdbc inside `bootJar`, driver loaded from `loader.path`; boot without the driver refused with the remedy — **PASS**
3. Cert bundle import (`oracle-b1-cert`) + `by-path` resolves — **PASS** (4 paths; CI imports twice and checks one deployment row)
4. Two JVMs on one schema — second non-clustered JVM **PASS** (`:18093` health UP). Live lease reclaim is not asserted with cluster off; lease failover stays on CI (`OracleFlywayBaselineTckTest`)
5. Audit chain — serial re-import **PASS** (`audit_event_chain` rows > 0). Concurrent append (8 writers) + hash verify is CI (`OracleFlywayBaselineTckTest`)
6. Job claim with `SKIP LOCKED` through `PlatformJobService` (two concurrent claimers get different jobs) — **PASS** (CI)
7. Boot refuse for row isolation, `HARD`, JDBC history, dual-write, JDBC journal, cluster on Oracle — **PASS**; app schema switch fail-closed (`OracleDialect.supportsSessionSchemaSwitch=false`)
8. Runtime-settings `PATCH` cannot hot-enable the JDBC event journal — **PASS**

Evidence: [`tools/oracle-b1-lab/gate-evidence-lab-free.json`](../../tools/oracle-b1-lab/gate-evidence-lab-free.json) — 18 PASS / 0 FAIL.

## Before any Prod Yes signature

| Item | State |
|------|-------|
| Named edition | Oracle Database Enterprise Edition (or Standard Edition 2) 19c or 23ai/26ai on a host the operator runs, or one named managed service. Free is not that edition |
| 19c | DDL and dialect SQL follow 19c rules (no `BOOLEAN`, `FROM DUAL`, no row limit with `FOR UPDATE`) but have only run on 23.26 Free |
| Hibernate validate | `ddl-auto=validate` not run on Oracle; until it is fixed or waived in writing, non-`local` deployments set `SPRING_JPA_HIBERNATE_DDL_AUTO=none` |
| Empty strings | Oracle stores `''` as `NULL`; a `''` write into a `NOT NULL` text column fails with ORA-01400. Review paths the gate does not exercise |
| Uncovered SQL | PostgreSQL-only SQL on paths this gate does not exercise (bucket aggregate, `CLOB` compared with `=`, SQL-binding concatenation) reviewed or fenced |
| Network | Oracle Net encryption / TLS and wallet on the named host |
| Re-run | All eight gates above on the named cell |

**Tech debt P-ORACLE-SKU:** a Prod Yes needs this checklist on a named non-Free edition. Each further edition or managed service (Standard Edition 2, Enterprise Edition, Autonomous Database, Amazon RDS for Oracle, and others) needs its own run before a claim names it.

### Lab rehearsal

| Evidence | Where |
|----------|--------|
| CI TCK / boot fences | `Oracle*` tests (`-Dispf.test.oracleDocker=true` adds the lab driver and runs the Docker TCKs) |
| Local Oracle Free stand | [`tools/oracle-b1-lab/stand-local.ps1`](../../tools/oracle-b1-lab/stand-local.ps1) |
| Gate collector | [`tools/oracle-b1-lab/gate-evidence.ps1`](../../tools/oracle-b1-lab/gate-evidence.ps1) |

Signer / date: local Oracle Database Free lab gate / 2026-11-22 (Lab green only)

Related: [0080](decisions/0080-oracle-metadata-certification.md), [oracle-certification-epic](oracle-certification-epic.md), [tools/oracle-b1-lab](../../tools/oracle-b1-lab/README.md).
