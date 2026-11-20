# MSSQL single-engine profile

Opt-in lab/profile for metadata + JDBC journal + JDBC history on one SQL Server database.

This is **not** Prod Yes and **not** a 10-year / 20-second certificate. See [mssql-single-engine-ems-epic](../../docs/en/mssql-single-engine-ems-epic.md).

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
