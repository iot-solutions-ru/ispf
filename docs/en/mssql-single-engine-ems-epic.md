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

**Signed:** ADR-0037 **Prod Yes (B1)** on SQL Server 2022 Enterprise Evaluation `16.0.4325.4` ([checklist](mssql-sku-promotion-checklist.md)). Licensed Standard / Enterprise / Azure SQL DB|MI cells still need their own re-run.

### Gate B — B2-core (capped retention)

Opt-in `ISPF_MSSQL_SINGLE_ENGINE=true` plus `ISPF_HISTORIAN_DEPLOY_PROFILE=mssql-single-engine`.

- JDBC journal and JDBC variable history are allowed on `ISPF_DB`.
- Boot refuses warm tier, cold archive, dual-write, cluster, tenant row isolation, and a non-JDBC history/journal store.
- Bucket aggregate SQL uses `DATE_BUCKET` (SQL Server 2022+) and filters on `observed_at` (SARGable).
- Event-history purge on MSSQL deletes in batches (`DELETE TOP`) so one transaction does not hold a multi-year table.
- Retention cap until Gate C: do not claim 10 years.
- Testcontainers Developer edition is a smoke signal, not the §8 signature.

**Lab exit (2026-11-20):** journal + history JDBC on Evaluation `16.0.4325.4`, dedicated lab DB `ispf_b2`, retention floor 90 days, measured `DELETE TOP` purge of 20 000 `event_history` rows in ~0.5 s (`tools/mssql-b1-lab/gate-evidence-b2-core-evaluation.json`). This is **Gate B engineering**, not ADR-0037 **Prod Yes (B2)** (Gate C still open). ≤20 s plant-wide scans stay a non-claim.

### Gate C — B2-archive (before any Prod Yes B2)

Partition/PK rewrite is not applied to `V1`. In-place rewrite of `variable_samples` after rows exist is forbidden (ADR-0064).

**V101.1 foundation (migrated on the mssql pack):** unique series+time index `uq_variable_samples_series_time`, table `variable_sample_rollups`, table `historian_spool_batches`. See `MssqlHistorianDivergence.V101_1_FOUNDATION`.

**V101.2 Gate C1 (migrated, empty DB only):** rebuilds `variable_samples` to `PRIMARY KEY (sampled_at, id)` on monthly `RANGE RIGHT` partitions (`pf`/`ps_variable_samples_month`, `PRIMARY` filegroup, `LOCK_ESCALATION = AUTO`). Durable status in `mssql_historian_partition_status`: `APPLIED` or `SKIPPED_NONEMPTY`. Nonempty databases keep the IDENTITY PK and the 90-day `DELETE` retention path. Operator horizon extend: `tools/mssql-b1-lab/split-variable-samples-horizon.sql` (SPLIT only — no SWITCH). TCK: `MssqlVariableSamplesPartitionTckTest`. See `MssqlHistorianDivergence.V101_2_PARTITIONS`.

**V101.3 Gate C2 (materializer writes only):** opt-in JDBC job (`ISPF_MSSQL_ROLLUP_MATERIALIZER`, default off) MERGEs `history_enabled` numeric series into `variable_sample_rollups` on **`sampled_at`** buckets `1h` / `1d`, walking a contiguous cursor in `mssql_historian_rollup_cursor` (oldest-first; not `MAX(bucket_start)`). Separate from the ClickHouse analytics materializer. Charts / `MssqlDialect` bucket SQL still scan raw rows by **`observed_at`**. App `DELETE` + `max-retention-days` stay the retention path. The cursor is **not** a SWITCH seal. TCK: `MssqlJdbcRollupMaterializerTckTest`. See `MssqlHistorianDivergence.V101_3_ROLLUP_CURSOR`.

Still open (later — not Prod Yes B2 yet):

- Monthly `SWITCH` / `TRUNCATE` retention by a maintenance login (runtime login stays without `ALTER`). Requires a later read-path cutover; C2 cursor alone does not authorize drops.
- Per-variable retention vs a dropped month: single-engine mode keeps one global floor (`max-retention-days`, default 90) until that cutover, because `SWITCH` drops a time slice for every variable.
- `event_history` partition retention (batched `DELETE TOP` is already in place).
- Wiring chart / query path onto `variable_sample_rollups` (or `observed_at` rollups).
- Automatic replay that relies on the V101.1 unique key (replay is still manual / off).
- Scale evidence on the named SKU under `FULL` recovery, filed under `docs/evidence/`. ADR-0078 §2b stays empty until that file exists.

### Gate D — JDBC buffer (resilience, not the first claim)

`ISPF_MSSQL_JDBC_BUFFER=true` writes failed **connectivity** batches to a local directory and rethrows. It does not acknowledge a sample as stored in SQL. Quota fail-closed. Replay is **not** automatic even though V101.1 adds a unique series+time key — wiring that key into safe replay is still open. Bytes on disk are outside backup. A dead node disk loses them. No cross-node copy.

### Gate E — Packaging and DR honesty

- Profile: [examples/mssql-single-engine/](../../examples/mssql-single-engine/).
- [ADR-0068](decisions/0068-backup-and-dr.md) rule 4: one database backup is T2+T3 together; T4 and the JDBC buffer are out; no RPO/RTO number until a drill. Sketch: [mssql-single-engine.md](../evidence/dr-restore/mssql-single-engine.md).

## What stays refused even after the gates

See ADR-0078 §2 (unbounded non-claims), §3 (no RLS claim), §4 (no app schema switch), §6 (greenfield only).
