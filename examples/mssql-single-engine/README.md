# MSSQL single-engine profile

Opt-in lab/profile for metadata + JDBC journal + JDBC history on one SQL Server database.

**Prod Yes (B2)** on Evaluation for capped JDBC history/journal ([B2 checklist](../../docs/en/mssql-b2-sku-promotion-checklist.md)). This is **not** a 10-year / ≤20-second certificate and **not** product SWITCH. See [mssql-single-engine-ems-epic](../../docs/en/mssql-single-engine-ems-epic.md).

```text
# source examples/mssql-single-engine/single-engine.env
```

Helm chart defaults stay on PostgreSQL (`deploy/helm/ispf/values.yaml`). To point a deployment at this profile, set the same environment variables on the server process. Do not enable `warmEnabled` together with this profile: boot refuses it.

Before boot the DBA must:

- dedicated instance (B2);
- `READ_COMMITTED_SNAPSHOT ON`;
- a case-sensitive collation (`_CS_` in the name).

`ISPF_MSSQL_SINGLE_ENGINE=true` checks those two database flags and refuses to start if they are missing. History store must be `jdbc` (not `jpa`).

**Partitions (Gate C1):** Flyway `V101.2` rebuilds `variable_samples` only when the table is empty (`mssql_historian_partition_status.status = APPLIED`). A database that already has samples records `SKIPPED_NONEMPTY` and keeps the 90-day DELETE retention path — create a new empty database if you need monthly partitions. Extend the empty horizon with `tools/mssql-b1-lab/split-variable-samples-horizon.sql` (SPLIT only; no SWITCH).

**Rollups (Gate C2 / C chart-prep):** set `ISPF_MSSQL_ROLLUP_MATERIALIZER=true` to MERGE ingest-time (`sampled_at`) hour/day buckets into `variable_sample_rollups`. Set `ISPF_MSSQL_OBSERVED_ROLLUP_MATERIALIZER=true` to fill `variable_sample_observed_rollups` (recomputes touched `observed_at` buckets; default off). Set `ISPF_MSSQL_OBSERVED_ROLLUP_READ=true` to stitch closed `observed_at` buckets into `/history/aggregate` with raw head/tail (`dataSource=observed_rollup`; default off). Report-only ingest buckets: `GET /api/v1/objects/by-path/variables/history/ingest-rollups?bucket=1h|1d`. Cursors are not permission to SWITCH/TRUNCATE a month. **Prod Yes (B2)** on Evaluation: [B2 checklist](../../docs/en/mssql-b2-sku-promotion-checklist.md) (DELETE + 90-day ceiling in force).
