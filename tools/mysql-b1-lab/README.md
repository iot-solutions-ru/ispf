# MySQL B1 / B2 lab harness (ADR-0079)

Operator scripts for a **single-node** MySQL 8.4 lab stand. Not a production deploy path. The stand is not the signature: Prod Yes (B1) is signed in the [checklist](../../docs/en/mysql-sku-promotion-checklist.md), Prod Yes (B2) single-engine in the [B2 checklist](../../docs/en/mysql-b2-sku-promotion-checklist.md).

| Script / file | Role |
|---------------|------|
| `stand-local.ps1` | MySQL 8.4 Community (`mysql:8.4`, `:13306`) + branch `bootJar` (`:18090`); Hibernate `ddl-auto=validate`; history/journal off |
| `gate-evidence.ps1` | Gate collector (20 items) against that stand; writes only what it measured to `-Out` |
| `gate-evidence-lab-community.json` | Collector output, signed Lab-green run: MySQL 8.4.13 Community, 2026-11-22, 17 PASS / 0 FAIL (the 17 items of that version) — [checklist](../../docs/en/mysql-sku-promotion-checklist.md) |
| `gate-evidence-validate-community.json` | Collector output, re-run on the same cell: `ddl-auto=validate` on the stand JVM and the second JVM (`docker inspect`), `lower_case_table_names` read, platform metrics and the job queue (2026-11-22, 20 PASS / 0 FAIL) |
| `gate-evidence-prod-yes-community.json` | Owner decision, no measured items: the owner-extended **Prod Yes (B1)** cell, MySQL Community Server 8.4.x + MariaDB Connector/J, with its tech debt and non-claims |
| `mysql-b1-cert-bundle.json` | Tree-only cert bundle; mirrors `packages/ispf-server/src/test/resources/mysql-b1-cert-bundle.json` — keep them in sync |
| `stand-b2-local.ps1` | B2 single-engine stand: same MySQL container, fresh dedicated database `ispf_b2`, `bootJar` on `:18094` with `ISPF_MYSQL_SINGLE_ENGINE=true`, JDBC history + journal, `ddl-auto=validate` |
| `gate-b2.ps1` | B2 gate collector (25 items) against that stand: live historian write, bucket aggregate, raw read, retention ceiling, live journal, journal purge, boot and preflight refusals; writes only what it measured to `-Out` |
| `gate-evidence-b2-community.json` | Collector output, B2 run on MySQL 8.4.13 Community, 2026-11-23, 25 PASS / 0 FAIL |
| `gate-evidence-b2-prod-yes-community.json` | Owner decision, no measured items: **Prod Yes (B2)** on the B1 cell with the single-engine flag, with tech debt and non-claims |

```powershell
$env:MYSQL_LAB_PASSWORD = "<lab throwaway>"
.\gradlew.bat :packages:ispf-server:bootJar
powershell -File tools/mysql-b1-lab/stand-local.ps1
powershell -File tools/mysql-b1-lab/gate-evidence.ps1 -Out tools/mysql-b1-lab/gate-evidence-<cell>.json -EngineNote "<named cell>"
# B2 single-engine
powershell -File tools/mysql-b1-lab/stand-b2-local.ps1
powershell -File tools/mysql-b1-lab/gate-b2.ps1 -Out tools/mysql-b1-lab/gate-evidence-b2-<cell>.json -EngineNote "<named cell>"
```

CI-side TCKs (Docker, opt-in): `.\gradlew.bat :packages:ispf-server:test --tests "com.ispf.server.relational.Mysql*" -Dispf.test.mysqlDocker=true`.

## B1 fences (required env)

- `ISPF_METADATA_DB_KIND=mysql`
- `ISPF_TENANT_DB_ROW_ISOLATION=false` (and isolation mode not `HARD`)
- `ISPF_VARIABLE_HISTORY_ENABLED=false` (or a non-JDBC store), `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`
- `ISPF_EVENT_JOURNAL_ENABLED=false` (or a non-JDBC store)
- `ISPF_CLUSTER_ENABLED=false`

Boot refuses each of these otherwise, and refuses a MariaDB server under any kind.

## B2 single-engine (required env)

- `ISPF_MYSQL_SINGLE_ENGINE=true`, set explicitly (the `mysql-single-engine` deploy profile alone leaves ISPF in B1)
- `ISPF_VARIABLE_HISTORY_STORE=jdbc`, `ISPF_EVENT_JOURNAL_STORE=jdbc` (journal enabled at boot)
- `ISPF_HISTORIAN_TIER_WARM_ENABLED=false`, `ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=false`, `ISPF_VARIABLE_HISTORY_SPOOL_ENABLED=false`, `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`
- `ISPF_CLUSTER_ENABLED=false`, row isolation off, isolation mode not `HARD` — still refused with the flag
- Server `READ-COMMITTED` and the database on `utf8mb4_0900_bin`: the single-engine preflight refuses boot otherwise

## Connection

- Client: MariaDB Connector/J (LGPL) — `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver`. GPL `mysql-connector-j` is not used.
- URL: `jdbc:mysql://<host>:3306/ispf?permitMysqlScheme=true&…` — Connector/J 3.x refuses `jdbc:mysql:` without `permitMysqlScheme`.
- The stand adds `allowPublicKeyRetrieval=true` because it runs `caching_sha2_password` without TLS. That setting is lab-only. A production host uses TLS (`sslMode=verify-full`); Prod Yes (B1) does not certify TLS (**P-MYSQL-TLS**).
- Server: `--transaction-isolation=READ-COMMITTED`, database `ispf` on `utf8mb4` / `utf8mb4_0900_bin` (case-sensitive object paths).
- The stand grants `SELECT ON performance_schema.user_variables_by_thread` to the ISPF user; without it Flyway logs a 1142 warning and skips its user-variable reset.

## Notes

- `SPRING_PROFILES_ACTIVE=local` sets `ddl-auto=none`; every stand and gate JVM overrides it with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, the production default, so a boot also proves Hibernate schema validation on MySQL. The gate reads that variable back from the stand JVM and the second JVM with `docker inspect`.
- Do not edit collector output. An owner decision goes in its own JSON (`gate-evidence-prod-yes-community.json`), as in `tools/mssql-b1-lab`.
- ISPF pins `MysqlHibernateDialect` (a `MySQLDialect` subclass) because the MariaDB driver name would select `MariaDBDialect`; the app log shows `Database dialect: MysqlHibernateDialect`. Do not set `hibernate.dialect`: plain `MySQLDialect` fails `ddl-auto=validate` on `lower_case_table_names=0` ("missing table").
- MySQL DDL is not transactional. If Flyway V1 fails, drop and recreate `ispf`, then restart.
- PowerShell splits an unquoted `-h127.0.0.1` at the first dot; the scripts pass `--host=127.0.0.1` quoted.
- Ports bind to `127.0.0.1` by default (`BIND_HOST` to override). The gate also uses `:18091` (second JVM) and a throwaway `mariadb:11.4` container.
- The B2 stand uses `:18094` (`:18092` / `:18093` belong to the Oracle lab). Its gate boots refusal JVMs against throwaway databases `ispf_b2_fence` and `ispf_b2_ci` (a `utf8mb4_0900_ai_ci` default) and drops them afterwards.
- The B2 gate seeds `variable_samples` and `event_history` with SQL in UTC (`UTC_TIMESTAMP(6)`), matching how ISPF writes `DATETIME(6)`.

## Secrets

Set `MYSQL_LAB_PASSWORD` (lab throwaway; root and the `ispf` user). The scripts refuse to start if it is unset.
