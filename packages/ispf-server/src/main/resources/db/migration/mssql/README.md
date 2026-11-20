# MS SQL Server migration pack (ADR-0037 / ADR-0078)

Enterprise certification target. **Greenfield only.**

| Artifact | Status |
|----------|--------|
| `V1__baseline.sql` | Present (generated from PG pack; regenerate with `python tools/mssql-baseline-generate.py`) |
| Production claim | **Prod Yes (B1)** — Enterprise Evaluation `16.0.4325.4` ([checklist](../../../../../../../../docs/en/mssql-sku-promotion-checklist.md)). B2 and A-A not certified |

`ISPF_METADATA_DB_KIND=mssql` is **Prod Yes (B1)** on the signed Enterprise Evaluation cell ([sku checklist](../../../../../../../../docs/en/mssql-sku-promotion-checklist.md)). A production host still needs a licensed Standard/Enterprise or Azure SQL DB/MI.

Boot still **refuses** row isolation and `HARD` tenant mode. JDBC history, JDBC journal, dual-write, and `ISPF_CLUSTER_ENABLED=true` are allowed (B2 / active-active). They are **lab-proved** on the same Evaluation cell, not a separate Prod Yes:

- B2: history insert + 60s bucket aggregate, and one `event_history` row with `OFFSET/FETCH`, on database `ispf` (`:18082`, 2026-11-20). No Timescale. Retention is application `DELETE`, not partition `SWITCH`. 10-year raw archive and multi-year ≤20 s scans stay **non-claims**. B2 jdbc history expects a **dedicated instance** (no co-tenant OLTP).
- Active-active: two JVMs (`edge-api` `:18083`, `io` `:18084`) on one writable primary, NATS JetStream + Redis, `clusterEnabled=true`. Not an Availability Group failover soak.

Runtime: `MssqlDialect` — session schema switch unsupported (no `USE`); table-lock advisory; `FETCH NEXT` limits; unique codes `2627`/`2601`. Dedicated DB + RCSI + case-sensitive collation required. Config migration: new empty DB + bundle import.

**Solution SQL policy (B1):** function-script `exec` / shipped SQL that assumes PostgreSQL (`ON CONFLICT`, `search_path` app schemas, PG-only functions) against `ISPF_DB` is **unsupported** on MSSQL. Prefer an external JDBC `DATA_SOURCE` for report/solution SQL, or keep those solutions on PostgreSQL. A dialect-safe cert bundle may replace this non-claim later ([0078](../../../../../../../../docs/en/decisions/0078-mssql-metadata-certification.md)).

**Cert bundle:** tree-only fixture `mssql-b1-cert-bundle.json` (no migrations). Lab gate: `MssqlBundleImportTckTest` (skips without Docker).

Delivery board: [mssql-certification-epic](../../../../../../../../docs/en/mssql-certification-epic.md).

See [storage-portability-inventory](../../../../../../../../docs/en/storage-portability-inventory.md) and [ADR-0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md).
