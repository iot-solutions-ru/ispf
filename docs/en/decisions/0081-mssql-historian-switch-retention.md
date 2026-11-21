# ADR-0081: MSSQL historian monthly SWITCH retention (design only)

## Status

**Accepted as design** (2026-11-20). **Amended** (2026-11-21): empty Flyway staging DDL is allowed; JVM / scheduled `SWITCH` remain forbidden.

Lab script **exists** (`tools/mssql-b1-lab/switch-variable-samples-month.sql`, dry-run by default; requires explicit `MaxRetentionDays` and `ConfirmSwitch=YES`). Product retention **in force** remains application `DELETE` plus `ispf.relational.mssql.max-retention-days` (default 90). Charts stay on raw `observed_at`. ADR-0078 §2b stays empty. ADR-0037 **Prod Yes (B2)** stays closed.

### Amendment (2026-11-21) — staging DDL only

**Allowed (narrower productize):**

- A future MSSQL Flyway migration may create an empty `dbo.variable_samples_switch_staging` on `PRIMARY`, matching the lab script’s table/index shape (`IDENTITY` on `id`, `quality`, clustered PK `(sampled_at, id)`, matching unique/lookup indexes on `PRIMARY`, no foreign keys).
- The existing maintenance script remains the only operator entrypoint that may `SWITCH` / `TRUNCATE`. Confirm still requires `DryRun=0` and exact `ConfirmSwitch=YES`.
- **After** that Flyway DDL ships, update the lab script to **refuse** when staging is missing (remove confirm-time auto-create), nonempty, or not identical. Until then the current script may print a dry-run plan when staging is absent and create empty staging only on confirm.

**Still forbidden:**

- Flyway (or any migration) that performs `SWITCH`, `TRUNCATE` of a live month, or `ALTER PARTITION` for retention.
- Any in-process or scheduled JVM job that performs `SWITCH`.
- A maintenance password in application config.
- A TCK that switches a month on a shared or Evaluation database as CI proof.
- Filling §2b, Prod Yes B2, or ticking Gate C2+/C3 from staging DDL alone.

Privilege stays **maintenance-only** for mutation. Deploy / Flyway may create the empty staging table. The runtime login still has no `ALTER` for partition `SWITCH`.

## Context

Gate C1 (`V101.2`) can rebuild an **empty** `variable_samples` to `PRIMARY KEY (sampled_at, id)` on monthly `RANGE RIGHT` partitions and record `mssql_historian_partition_status` = `APPLIED`. Nonempty databases stay `SKIPPED_NONEMPTY` forever for that database ([0064](0064-release-compatibility-lts.md)). Gate C2 writes optional ingest-time (`sampled_at`) rollups; the coverage cursor and `GET .../history/ingest-rollups` are report-only and are **not** a seal to drop raw months ([0078](0078-mssql-metadata-certification.md), [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md)).

Archive exit still needs a way to drop whole UTC months without relying on unbounded `DELETE`. That path must not give the ISPF runtime login `ALTER`, must not treat rollup presence as permission to destroy raw series the materializer never copied, and must not claim Prod Yes B2 from a design document.

## Decision

All of the following are one **AND**. A later implementation PR is **R4** and starts from this ADR; it is not a follow-up commit on a docs branch.

### Privilege and host

- Run only under a **maintenance login** outside the JVM, same operator shape as `tools/mssql-b1-lab/split-variable-samples-horizon.sql`.
- The ISPF **runtime** login keeps no `ALTER` on partition schemes/functions/tables for this purpose.
- No maintenance secret in application config. No in-process schedule that performs `SWITCH`.

### Preconditions (refuse otherwise)

- `mssql_historian_partition_status.status = APPLIED` for this database.
- Every index on `variable_samples`, including `uq_variable_samples_series_time`, is aligned to the monthly partition scheme.
- `SKIPPED_NONEMPTY` is permanent for that database. This ADR has **no** online rebuild and no in-place rewrite after rows exist.
- Staging table is empty and identical (columns, nullability, keys, indexes, filegroup) before any move.

### Drop mechanics (prose only)

- One UTC month per run, **oldest non-empty eligible** only (skip already-empty historical partitions left after prior drops; boundaries are not `MERGE`d away). No multi-month parameter.
- Month half-open interval `[start, next)` is eligible only when `next` is at or before `now − max-retention-days`. A month that still overlaps the retention window stays. Ninety days is **not** a month boundary; keep the extra partial month.
- Intended sequence: `SWITCH` the month into staging, then `TRUNCATE` staging. `SWITCH` alone is a move, not a drop.
- Global floor wins. Per-variable “keep longer” is not honored and does not create a second scheme.

### Destruction list (gone with the month, not on rollups)

Dropping a raw month destroys every series in that `sampled_at` slice, including rows the optional materializer never copied:

- non-numeric / `value_text` series
- `field_name = $record`
- `history_enabled = 0`
- `quality` (no such column on `variable_sample_rollups`)
- a row whose `observed_at` calendar month is not its `sampled_at` calendar month

Operator charts remain on `observed_at`. This ADR does not change that clock and does not authorize chart cutover onto ingest rollups.

### Non-seals

- The rollup coverage cursor and the ingest-rollups report are **not** a seal and **not** a precondition for dropping a month.
- `MERGE` into `variable_sample_rollups` does not prove the month was complete, does not delete rollup rows when raw rows vanish, and a missing cursor falls back to raw `observed_at` aggregate.
- Do not add a coverage percentage gate in this design.

### Catch-all and horizon

- Left catch-all `sampled_at < 2024-01-01`: no partition `MERGE`, no `SWITCH`, until a separate backdate policy exists.
- Rightmost and future partitions stay on the existing `SPLIT` operator script only.

### Explicit non-claims

- No log-versus-`DELETE` performance claim under `FULL` recovery.
- No `ONLINE` index rebuild requirement or authorization.
- No fill of ADR-0078 §2b. No ADR-0037 **Prod Yes (B2)**.
- No executable `SWITCH` SQL **in this ADR text**. The lab script under `tools/mssql-b1-lab/` must keep the guards in this Decision. Empty staging DDL (Flyway) is authorized only by the 2026-11-21 amendment above.

## Consequences

- Implementers cannot treat `APPLIED` alone, rollup rows, or the report API as permission to drop.
- Sites on `SKIPPED_NONEMPTY` stay on `DELETE` + the retention ceiling until they rebuild on a new empty database (already Gate C1 policy). Staging DDL on those databases is inert while the script refuses non-`APPLIED`.
- The next code PR after this amendment may add empty staging DDL plus a script update that refuses missing/nonempty/non-identical staging (still not wired into stands/CI). It must not add a JVM schedule, confirm in CI, chart cutover, §2b, or Prod Yes B2.

## Related

- [0064-release-compatibility-lts](0064-release-compatibility-lts.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md)
- [mssql-certification-epic](../mssql-certification-epic.md)
- [known-limitations](../known-limitations.md)
