# MS SQL Server migration pack (ADR-0037 / ADR-0078)

Enterprise certification target. **Greenfield only.**

| Artifact | Status |
|----------|--------|
| `V1__baseline.sql` | **Frozen** greenfield squash. Do not regenerate after any site has applied it. Later changes are incremental `Vnn` twins or a named historian divergence ([single-engine epic](../../../../../../../../docs/en/mssql-single-engine-ems-epic.md)) |
| Production claim | **Not certified** until the [0078](../../../../../../../../docs/en/decisions/0078-mssql-metadata-certification.md) gate |

Do **not** set `ISPF_METADATA_DB_KIND=mssql` in production until the named-SKU §8 gate passes ([sku checklist](../../../../../../../../docs/en/mssql-sku-promotion-checklist.md)). Lab/B1 boot fences (refuse otherwise):

- `ISPF_TENANT_DB_ROW_ISOLATION=false`
- `ISPF_VARIABLE_HISTORY_ENABLED=false` **or** non-JDBC store (`clickhouse` / `cassandra` / `scylla`); dual-write off
- `ISPF_EVENT_JOURNAL_ENABLED=false` **or** non-JDBC journal store
- `ISPF_CLUSTER_ENABLED=false` (A-A **not certified** on MSSQL until soak)

Runtime: `MssqlDialect` — session schema switch unsupported (no `USE`); table-lock advisory; `FETCH NEXT` limits; unique codes `2627`/`2601`. Dedicated DB + RCSI + case-sensitive collation required. Config migration: new empty DB + bundle import.

**Solution SQL policy (B1):** function-script `exec` / shipped SQL that assumes PostgreSQL (`ON CONFLICT`, `search_path` app schemas, PG-only functions) against `ISPF_DB` is **unsupported** on MSSQL. Prefer an external JDBC `DATA_SOURCE` for report/solution SQL, or keep those solutions on PostgreSQL. A dialect-safe cert bundle may replace this non-claim later ([0078](../../../../../../../../docs/en/decisions/0078-mssql-metadata-certification.md)).

**Cert bundle:** tree-only fixture `mssql-b1-cert-bundle.json` (no migrations). Lab gate: `MssqlBundleImportTckTest` (skips without Docker).

**Single-engine (opt-in, not Prod Yes):** `ISPF_MSSQL_SINGLE_ENGINE=true` allows JDBC history and journal on this database and refuses warm tier, cold archive, dual-write, and cluster. Boot also requires RCSI and a `_CS_` collation. Retention clamps to `ispf.relational.mssql.max-retention-days` (default 90) until monthly partitions land. `V101.1__single_engine_historian_divergence.sql` adds the unique series+time index, `variable_sample_rollups`, and `historian_spool_batches` — not the partition/PK rewrite. Profile: [examples/mssql-single-engine](../../../../../../../../examples/mssql-single-engine/README.md).

Delivery board: [mssql-certification-epic](../../../../../../../../docs/en/mssql-certification-epic.md).

See [storage-portability-inventory](../../../../../../../../docs/en/storage-portability-inventory.md) and [ADR-0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md).
