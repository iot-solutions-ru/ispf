# MySQL B1 lab harness (ADR-0079)

Operator scripts for a **single-node** MySQL 8.4 lab stand. Not a production deploy path. The stand is not the signature: Prod Yes (B1) is signed in the [checklist](../../docs/en/mysql-sku-promotion-checklist.md).

| Script / file | Role |
|---------------|------|
| `stand-local.ps1` | MySQL 8.4 Community (`mysql:8.4`, `:13306`) + branch `bootJar` (`:18090`); Hibernate `ddl-auto=validate`; history/journal off |
| `gate-evidence.ps1` | Lab-green gate collector (17 items) against that stand; writes JSON (`-Out`) |
| `gate-evidence-lab-community.json` | Signed Lab-green run: MySQL 8.4.13 Community, 2026-11-22, 17 PASS / 0 FAIL — [checklist](../../docs/en/mysql-sku-promotion-checklist.md) |
| `gate-evidence-prod-yes-community.json` | Re-run on the same cell with `ddl-auto=validate` on every JVM (2026-11-22, 17 PASS / 0 FAIL) — basis of the owner-extended **Prod Yes (B1)** cell, MySQL Community Server 8.4.x + MariaDB Connector/J |
| `mysql-b1-cert-bundle.json` | Tree-only cert bundle; mirrors `packages/ispf-server/src/test/resources/mysql-b1-cert-bundle.json` — keep them in sync |

```powershell
$env:MYSQL_LAB_PASSWORD = "<lab throwaway>"
.\gradlew.bat :packages:ispf-server:bootJar
powershell -File tools/mysql-b1-lab/stand-local.ps1
powershell -File tools/mysql-b1-lab/gate-evidence.ps1 -Out tools/mysql-b1-lab/gate-evidence-lab-community.json -EngineNote "<named cell>"
```

CI-side TCKs (Docker, opt-in): `.\gradlew.bat :packages:ispf-server:test --tests "com.ispf.server.relational.Mysql*" -Dispf.test.mysqlDocker=true`.

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=mysql`
- `ISPF_TENANT_DB_ROW_ISOLATION=false` (and isolation mode not `HARD`)
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or a non-JDBC store), `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or a non-JDBC store)
- `ISPF_CLUSTER_ENABLED=false`

Boot refuses each of these otherwise, and refuses a MariaDB server under any kind. There is no single-engine bypass on MySQL.

## Connection

- Client: MariaDB Connector/J (LGPL) — `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver`. GPL `mysql-connector-j` is not used.
- URL: `jdbc:mysql://<host>:3306/ispf?permitMysqlScheme=true&…` — Connector/J 3.x refuses `jdbc:mysql:` without `permitMysqlScheme`.
- The stand adds `allowPublicKeyRetrieval=true` because it runs `caching_sha2_password` without TLS. That setting is lab-only. A production host uses TLS (`sslMode=verify-full`); Prod Yes (B1) does not certify TLS (**P-MYSQL-TLS**).
- Server: `--transaction-isolation=READ-COMMITTED`, database `ispf` on `utf8mb4` / `utf8mb4_0900_bin` (case-sensitive object paths).
- The stand grants `SELECT ON performance_schema.user_variables_by_thread` to the ISPF user; without it Flyway logs a 1142 warning and skips its user-variable reset.

## Notes

- `SPRING_PROFILES_ACTIVE=local` sets `ddl-auto=none`; every stand and gate JVM overrides it with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, the production default, so a boot also proves Hibernate schema validation on MySQL.
- ISPF pins `MysqlHibernateDialect` (a `MySQLDialect` subclass) because the MariaDB driver name would select `MariaDBDialect`; the app log shows `Database dialect: MysqlHibernateDialect`. Do not set `hibernate.dialect`: plain `MySQLDialect` fails `ddl-auto=validate` on `lower_case_table_names=0` ("missing table").
- MySQL DDL is not transactional. If Flyway V1 fails, drop and recreate `ispf`, then restart.
- PowerShell splits an unquoted `-h127.0.0.1` at the first dot; the scripts pass `--host=127.0.0.1` quoted.
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override). The gate also uses `:18091` (second JVM) and a throwaway `mariadb:11.4` container.

## Secrets

Set `MYSQL_LAB_PASSWORD` (lab throwaway; root and the `ispf` user). The scripts refuse to start if it is unset.
