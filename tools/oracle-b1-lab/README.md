# Oracle B1 lab harness (ADR-0080)

Operator scripts for a **single-node** Oracle Database Free lab stand. Not a production deploy path. The stand is not the signature: Prod Yes (B1) on Oracle Database Free 23.26.x is signed in the [checklist](../../docs/en/oracle-sku-promotion-checklist.md).

| Script / file | Role |
|---------------|------|
| `stand-local.ps1` | Oracle Database Free (`gvenzl/oracle-free:23-slim-faststart`, `:11521`) + branch `bootJar` (`:18092`) with the driver on `loader.path`; history/journal off |
| `gate-evidence.ps1` | Gate collector (18 items) against that stand; writes only what it measured to `-Out` |
| `lab-common.ps1` | Shared helpers (SQL*Plus inside the container, ISPF start through `PropertiesLauncher`) |
| `gate-evidence-lab-free.json` | Collector output, signed Lab-green run: Oracle AI Database 26ai Free 23.26.4.0.0, 2026-11-22, 18 PASS / 0 FAIL — the basis of the Prod Yes (B1) sign ([checklist](../../docs/en/oracle-sku-promotion-checklist.md)) |
| `gate-evidence-prod-yes-free.json` | Owner decision, no measured items: the owner-override **Prod Yes (B1)** cell, Oracle Database Free 23.26.x + operator-supplied `ojdbc17`, with its tech debt and non-claims |
| `oracle-b1-cert-bundle.json` | Tree-only cert bundle; mirrors `packages/ispf-server/src/test/resources/oracle-b1-cert-bundle.json` — keep them in sync |

```powershell
$env:ORACLE_LAB_PASSWORD = "<lab throwaway>"
.\gradlew.bat :packages:ispf-server:bootJar :packages:ispf-server:copyOracleLabJdbc
powershell -File tools/oracle-b1-lab/stand-local.ps1
powershell -File tools/oracle-b1-lab/gate-evidence.ps1 -Out tools/oracle-b1-lab/gate-evidence-<cell>.json -EngineNote "<named cell>"
```

Docker TCKs (opt-in, not run by default CI; the flag also puts the lab driver on the test classpath): `.\gradlew.bat :packages:ispf-server:test --tests "*Oracle*" -Dispf.test.oracleDocker=true`.

## Driver

ISPF does not ship an Oracle JDBC driver. `copyOracleLabJdbc` copies `ojdbc17` (version from the Spring Boot BOM) into `packages/ispf-server/build/oracle-lab-jdbc/` for this lab; pass `-OjdbcJar` to use another copy. The scripts mount it at `/app/lib` and start `java -Dloader.path=/app/lib -cp /app/ispf-server.jar org.springframework.boot.loader.launch.PropertiesLauncher`, the same path an operator uses ([checklist § Adding the Oracle JDBC driver](../../docs/en/oracle-sku-promotion-checklist.md#adding-the-oracle-jdbc-driver)). The gate checks that `bootJar` carries no ojdbc and that boot without the mount is refused.

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=oracle`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver`
- `ISPF_TENANT_DB_ROW_ISOLATION=false` (and isolation mode not `HARD`)
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or a non-JDBC store), `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or a non-JDBC store)
- `ISPF_CLUSTER_ENABLED=false`

Boot refuses each of these otherwise. There is no single-engine bypass on Oracle.

## Connection

- URL: `jdbc:oracle:thin:@//<host>:1521/FREEPDB1`; user `ispf` (created by the image's `APP_USER`). An Oracle schema is a database user.
- The stand has no Oracle Net encryption. A production host configures it (or TLS with a wallet); Prod Yes (B1) does not certify it (**P-ORACLE-TLS**).

## Notes

- `SPRING_PROFILES_ACTIVE=local` keeps `ddl-auto=none`, as on the MSSQL B1 stand. Outside `local` set `SPRING_JPA_HIBERNATE_DDL_AUTO=none`: `validate` has not run on Oracle, and Prod Yes (B1) requires `none` (**P-ORACLE-VALIDATE**).
- Do not edit collector output. An owner decision goes in its own JSON (`gate-evidence-prod-yes-free.json`), as in `tools/mysql-b1-lab`.
- Hibernate logs HHH100123 (default JDBC fetch size 10, the ojdbc default) — a tuning hint, not an error.
- Oracle DDL auto-commits. If Flyway V1 fails, drop and recreate the `ispf` user, then restart.
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override). The gate also uses `:18093` (second JVM).

## Secrets

Set `ORACLE_LAB_PASSWORD` (lab throwaway; `SYSTEM` and the `ispf` user). The scripts refuse to start if it is unset.
