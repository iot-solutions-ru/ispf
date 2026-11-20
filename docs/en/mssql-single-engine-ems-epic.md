# Epic: MSSQL single-engine (metadata + journal + history)

> **Language:** Canonical English. Russian summary: [ru/mssql-single-engine-ems-epic.md](../ru/mssql-single-engine-ems-epic.md).  
> **Status:** Draft — delivery board under [ADR-0078](decisions/0078-mssql-metadata-certification.md) §2b.  
> **Policy:** Does **not** erase the unbounded non-claims in ADR-0078 §2. Does **not** set Prod Yes.

## Claim this epic is allowed to grow into

New **single-tenant** installs can keep metadata, the event journal, and variable history in **one dedicated SQL Server instance** of a **named edition**, under fail-closed profile `mssql-single-engine`, inside **named fixtures** (tags × interval × window × query text × p95).

Not this epic: Part 11 / a validated EMS, multi-tenant RLS, ISPF active-active, an unbounded 10-year raw scan ≤ 20 s, RPO = 0 if the node disk dies, disaster recovery from a `.bak` alone, live PostgreSQL → MSSQL migration.

## Locked defaults

| Choice | Value |
|--------|--------|
| HA | One ISPF JVM + SQL Server availability-group listener. `ispf.cluster.enabled` stays refused |
| First SKU to sign | One named SQL Server 2022 Standard **or** Azure SQL MI build. Archive features that need Enterprise are called out before that gate, not assumed |
| Partition grain | Monthly |
| ClickHouse / Parquet | Refused while `ISPF_MSSQL_SINGLE_ENGINE=true` |
| JDBC file buffer | Off by default. After B2-core. Not part of the first “one database” sentence |
| MySQL / Oracle | Deferred (ADR-0079 / ADR-0080) |

## Capacity sheet (boundary, not a measured SLO)

350 tags at 1 sample/minute for 10 years is about **1.8 billion** rows. The same count at 1 Hz is about 60× larger. A fixture must state interval, row width, journal rate, and window before anyone quotes it. SQL Server allows 15,000 partitions per table: hourly slices over 10 years do not fit; monthly does.

## Gates (sell separately)

### Gate A — B1 Prod Yes

Engineering in repo: `V1__baseline.sql` is frozen as the greenfield squash (do not regenerate after a site has applied it). Boot preflight for RCSI + a `_CS_` collation runs only when single-engine mode is on.

**Still open:** a human signature on one non-Developer cell of [mssql-sku-promotion-checklist](mssql-sku-promotion-checklist.md). This epic does **not** move ADR-0037 to Prod Yes (B1).

### Gate B — B2-core (capped retention)

Opt-in `ISPF_MSSQL_SINGLE_ENGINE=true` plus `ISPF_HISTORIAN_DEPLOY_PROFILE=mssql-single-engine`.

- JDBC journal and JDBC variable history are allowed on `ISPF_DB`.
- Boot refuses warm tier, cold archive, dual-write, cluster, tenant row isolation, and a non-JDBC history/journal store.
- Bucket aggregate SQL uses `DATE_BUCKET` (SQL Server 2022+) and filters on `observed_at` (SARGable).
- Event-history purge on MSSQL deletes in batches (`DELETE TOP`) so one transaction does not hold a multi-year table.
- Retention cap until Gate C: do not claim 10 years.
- Testcontainers Developer edition is a smoke signal, not the §8 signature.

**Exit sentence:** journal + history JDBC on the same dedicated instance, retention capped to N named in the fixture.

### Gate C — B2-archive (before any Prod Yes B2)

Not applied to `V1`. In-place rewrite of `variable_samples` after a production B2 install is forbidden (ADR-0064).

Target, specified in `MssqlHistorianDivergence` and **not migrated**:

- Partition key (`sampled_at`) in every unique index and the primary key.
- Monthly `SPLIT` ahead of the clock; `SWITCH` / `TRUNCATE` by a maintenance login. The runtime login does not mutate raw rows.
- Per-variable retention vs a dropped month: single-engine mode uses one global floor (the maximum retention), documented, because `SWITCH` drops a time slice for every variable.
- `event_history` gets the same class of retention (batched delete now; partition later).
- JDBC rollup table so multi-year reports do not scan raw `variable_samples`. The ClickHouse rollup services are not that table.
- Unique key including variable + `sampled_at` **before** any automatic replay.
- Scale evidence on the named SKU under `FULL` recovery, filed under `docs/evidence/`. ADR-0078 §2b stays empty until that file exists.

### Gate D — JDBC buffer (resilience, not the first claim)

`ISPF_MSSQL_JDBC_BUFFER=true` writes failed **connectivity** batches to a local directory and rethrows. It does not acknowledge a sample as stored in SQL. Quota fail-closed. Replay is **not** automatic: without the Gate C unique key, replay can duplicate rows. Bytes on disk are outside backup. A dead node disk loses them. No cross-node copy.

### Gate E — Packaging and DR honesty

- Profile: [examples/mssql-single-engine/](../../examples/mssql-single-engine/).
- [ADR-0068](decisions/0068-backup-and-dr.md) rule 4: one database backup is T2+T3 together; T4 and the JDBC buffer are out; no RPO/RTO number until a drill. Sketch: [mssql-single-engine.md](../evidence/dr-restore/mssql-single-engine.md).

## What stays refused even after the gates

See ADR-0078 §2 (unbounded non-claims), §3 (no RLS claim), §4 (no app schema switch), §6 (greenfield only).
