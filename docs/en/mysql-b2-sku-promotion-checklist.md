# MySQL B2 SKU promotion checklist (ADR-0079 single-engine)

> **Language:** Canonical English.  
> **Status:** **Prod Yes (B2) — signed 2026-11-23** on the B1 cell — **MySQL Community Server 8.4.x + MariaDB Connector/J only** — by owner extension from the B2 lab gate (25 PASS / 0 FAIL). Opt-in: B1 stays the default. Active-active is **not** part of this claim; boot still refuses `ISPF_CLUSTER_ENABLED=true` on MySQL.

Promote opt-in `ISPF_MYSQL_SINGLE_ENGINE=true` (JDBC history and journal on the MySQL `ISPF_DB`) only on a signed named cell. The B1 checklist for the same cell stays in force: [mysql-sku-promotion-checklist](mysql-sku-promotion-checklist.md).

## Named cell (Prod Yes B2)

| Field | Value |
|-------|-------|
| Product | **MySQL Community Server 8.4.x** LTS, self-managed. Evidence: 8.4.13 `MySQL Community Server - GPL`, image `mysql:8.4` (local AMD64 Docker) |
| JDBC client | **MariaDB Connector/J only** — 3.5.10, `org.mariadb.jdbc.Driver`, `jdbc:mysql://…?permitMysqlScheme=true` |
| Isolation | `transaction_isolation=READ-COMMITTED` — the single-engine preflight checks the pool's session and refuses boot otherwise |
| Charset / collation | ISPF database on `utf8mb4` / `utf8mb4_0900_bin` — the preflight refuses another database default |
| Dedicated database | Yes (`ispf_b2` on the evidence cell) |
| Dedicated instance | Required in production: no co-tenant OLTP on the MySQL server. The lab container hosted only ISPF databases |
| `ISPF_MYSQL_SINGLE_ENGINE` | `true`, set explicitly ([Deploy profile](#deploy-profile)) |
| History / journal | JDBC: `ISPF_VARIABLE_HISTORY_STORE=jdbc`, `ISPF_EVENT_JOURNAL_ENABLED=true`, `ISPF_EVENT_JOURNAL_STORE=jdbc` |
| Retention ceiling | `ISPF_MYSQL_MAX_RETENTION_DAYS` — default **90** (the evidence cell ran the default) |
| Off (boot refuses them) | Warm tier, cold archive, ClickHouse spool, dual-write, cluster, row isolation, `HARD` |
| Hibernate | `ddl-auto=validate` (`MysqlHibernateDialect`) |
| TLS | Not used on the evidence cell; operator-required, **not certified** (**P-MYSQL-TLS**) |

## What Prod Yes (B2) means on this cell

- Platform metadata, variable history (`variable_samples`) and the event journal (`event_history`) in **one** MySQL database
- History write through the batched JDBC store; raw `/history` reads; `/history/aggregate` buckets computed in MySQL (`MysqlDialect.variableSampleBucketAggregateSql`, `TIMESTAMPDIFF` / `TIMESTAMPADD` on `observed_at`, epoch-aligned, independent of the session and JVM time zones)
- JDBC journal write, list and keyset paging; purge in `DELETE … LIMIT 5000` batches, one transaction per batch
- Retention in force: application `DELETE`; every variable's retention is clamped to `max-retention-days`, and an unlimited (≤0) retention becomes that ceiling
- Schema: `mysql/V1` as for B1; B2 adds no migration

## Explicit non-claims

- Active-active (`ISPF_CLUSTER_ENABLED=true`) on MySQL, with or without single-engine
- Historian rollups, partitions, or partition-drop retention on MySQL
- A file buffer in front of MySQL (the MSSQL `ISPF_MSSQL_JDBC_BUFFER` has no MySQL twin)
- A multi-year raw archive in MySQL; any raw-scan latency figure without a named fixture (the gate's 33 ms aggregate covers 300 samples, not a plant)
- An RPO / RTO figure: one MySQL backup holds metadata, history and journal together, but no restore drill is recorded for this profile ([ADR-0068](decisions/0068-backup-and-dr.md))
- MySQL Enterprise, HeatWave, Amazon RDS / Aurora MySQL, Azure Database for MySQL, and every other non-Community cell (**P-MYSQL-SKU-RERUN**); MySQL 8.0 and 9.x; MariaDB Server
- TLS on the `ISPF_DB` connection (**P-MYSQL-TLS**); full SQL parity with PostgreSQL (**P-MYSQL-PG-SQL**)

## Deploy profile

`ISPF_HISTORIAN_DEPLOY_PROFILE=mysql-single-engine` labels the deployment. It fills `ispf.relational.mysql.single-engine=true` and `ispf.historian.tiers.warm.enabled=false` only where no property source defines those keys, and the shipped `application.yml` defines both. On a measured boot with only the profile set, ISPF stayed in B1 and refused JDBC history. Set `ISPF_MYSQL_SINGLE_ENGINE=true` and `ISPF_HISTORIAN_TIER_WARM_ENABLED=false` explicitly, as [examples/mysql-single-engine](../../examples/mysql-single-engine/README.md) does. The `mssql-single-engine` profile behaves the same way.

## Gate evidence

| # | Item | Result |
|---|------|--------|
| 1 | Engine cell: 8.4.13 Community, `READ-COMMITTED`, `ispf_b2` on `utf8mb4_0900_bin`; health, login, Flyway `mysql/V1`, `MysqlHibernateDialect` with validate; stand env read back with `docker inspect` | **PASS** |
| 2 | Preflight: the `ispf` session runs `READ-COMMITTED` on `utf8mb4_0900_bin` and the stand reaches UP | **PASS** |
| 3 | Live historian write: 3 `PUT /variables` land in `variable_samples` through the JDBC writer; `GET /history` returns them | **PASS** |
| 4 | Bucket aggregate: 300 seeded samples, `bucket=1m` → 51 minute-aligned buckets whose counts sum to 300, `dataSource=raw`; raw `/history` returns 300 | **PASS** |
| 5 | Retention ceiling: a variable configured for 365 days; `/history/aggregate` without `from` returns the 10-day-old sample and not the 100-day-old one | **PASS** |
| 6 | Live journal: `POST /events/fire` writes `event_history`; `GET /events?before=` (keyset, JDBC store) lists it | **PASS** |
| 7 | Journal purge: 20,000 rows older than 90 days deleted through `POST /events/journal/purge` (561 ms), none left, a recent row kept | **PASS** |
| 8 | Boot refusals with the flag on: cluster, warm tier, cold archive, dual-write, `store=clickhouse`; and B1 (flag off) still refuses JDBC history | **PASS** (6) |
| 9 | Preflight refusals: `REPEATABLE-READ` pool session; database default `utf8mb4_0900_ai_ci` | **PASS** (2) |

| Run | Evidence | Result |
|-----|----------|--------|
| B2 gate (collector output, not edited by hand) | [`gate-evidence-b2-community.json`](../../tools/mysql-b1-lab/gate-evidence-b2-community.json) | 25 PASS / 0 FAIL, 2026-11-23 |
| Owner decision | [`gate-evidence-b2-prod-yes-community.json`](../../tools/mysql-b1-lab/gate-evidence-b2-prod-yes-community.json) | Prod Yes (B2), 2026-11-23 |
| CI (opt-in Docker, `-Dispf.test.mysqlDocker=true`) | `MysqlHistoryBucketTckTest`: bucket SQL under a non-UTC JVM and a non-UTC session `time_zone`, 8 h epoch alignment, scoped and global purge across batches | PASS |

Re-run: `tools/mysql-b1-lab/stand-b2-local.ps1`, then `tools/mysql-b1-lab/gate-b2.ps1` ([lab README](../../tools/mysql-b1-lab/README.md)).

## Owner extension (2026-11-23)

As for B1, the lab Docker cell stands in for a named self-managed MySQL Community Server 8.4.x host. A gate re-run on the first named host is recommended, not blocking. The owner instruction was to sign Community B2 this way if the gate ran green; it did.

Signer / date: owner extension from the local Community B2 gate / 2026-11-23

Related: [0079](decisions/0079-mysql-metadata-certification.md), [mysql-certification-epic](mysql-certification-epic.md), [B1 checklist](mysql-sku-promotion-checklist.md), [MSSQL B2 checklist](mssql-b2-sku-promotion-checklist.md), [tools/mysql-b1-lab](../../tools/mysql-b1-lab/README.md).
