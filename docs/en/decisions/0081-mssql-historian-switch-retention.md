# ADR-0081: MSSQL historian monthly SWITCH retention (design only)

## Status

**Accepted as design** (2026-11-20). Implementation **Not started**.

This ADR locks the rules for a future maintenance-login `SWITCH` / staging-`TRUNCATE` retention path on partitioned `variable_samples`. It does **not** authorize a script, Flyway migration, JVM job, scheduler, TCK that switches a month, or copy-pasteable `SWITCH` / `TRUNCATE` / `ALTER PARTITION` SQL.

Retention in force today remains application `DELETE` plus `ispf.relational.mssql.max-retention-days` (default 90). Charts stay on raw `observed_at`. ADR-0078 §2b stays empty. ADR-0037 **Prod Yes (B2)** stays closed.

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
- No executable SQL in this ADR. No stub script that only `RAISERROR`s — a stub is the file the next edit turns on.

## Consequences

- Implementers cannot treat `APPLIED` alone, rollup rows, or the report API as permission to drop.
- Sites on `SKIPPED_NONEMPTY` stay on `DELETE` + the retention ceiling until they rebuild on a new empty database (already Gate C1 policy).
- The first PR that adds a maintenance script or migration must re-open as R4, cite this ADR rule-by-rule, and still leave Prod Yes B2 unsigned until evidence and remaining Gate C exits are explicitly accepted.

## Related

- [0064-release-compatibility-lts](0064-release-compatibility-lts.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [mssql-single-engine-ems-epic](../mssql-single-engine-ems-epic.md)
- [mssql-certification-epic](../mssql-certification-epic.md)
- [known-limitations](../known-limitations.md)
