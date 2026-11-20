# MS SQL Server migration pack (ADR-0037 / ADR-0078)

Enterprise certification target. **Greenfield only.** Operator-risk B1 envelope — **not** a support or GxP claim.

| Artifact | Status |
|----------|--------|
| `V1__baseline.sql` | Present (generated from PG pack; regenerate with `python tools/mssql-baseline-generate.py`) |
| Production claim | **Not certified** (no Prod Yes). Absolute forbid waived for the B1 envelope in [0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md) |

`ISPF_METADATA_DB_KIND=mssql` may be used on **Standard / Enterprise / Azure SQL DB / MI** inside the B1 envelope.

### Boot-enforced fences (refuse startup)

- `ISPF_TENANT_DB_ROW_ISOLATION=false` (and not `HARD` tenant isolation)
- `ISPF_VARIABLE_HISTORY_ENABLED=false` **or** non-JDBC store (`clickhouse` / `cassandra` / `scylla`); dual-write off
- `ISPF_EVENT_JOURNAL_ENABLED=false` **or** non-JDBC journal store
- `ISPF_CLUSTER_ENABLED=false` (A-A **not certified**)

### Operator-verified DB / SKU preconditions (not checked at boot)

- Dedicated database; case-sensitive collation; `READ_COMMITTED_SNAPSHOT ON`
- Greenfield empty DB + bundle import
- Licensable SKU for production: Standard / Enterprise / Azure SQL DB / MI

**Lab/CI only:** SQL Server Developer, Express, Azure SQL Edge, Testcontainers. Named-SKU §8 checklist remains unsigned ([sku checklist](../../../../../../../../docs/en/mssql-sku-promotion-checklist.md)).

Runtime: `MssqlDialect` — session schema switch unsupported (no `USE`); table-lock advisory; `FETCH NEXT` limits; unique codes `2627`/`2601`.

**Solution SQL policy (B1):** function-script `exec` / shipped SQL that assumes PostgreSQL (`ON CONFLICT`, `search_path` app schemas, PG-only functions) against `ISPF_DB` is **unsupported** on MSSQL. Prefer an external JDBC `DATA_SOURCE` for report/solution SQL, or keep those solutions on PostgreSQL.

**Cert bundle:** tree-only fixture `mssql-b1-cert-bundle.json` (no migrations). Lab gate: `MssqlBundleImportTckTest` (skips without Docker).

Delivery board: [mssql-certification-epic](../../../../../../../../docs/en/mssql-certification-epic.md).

See [storage-portability-inventory](../../../../../../../../docs/en/storage-portability-inventory.md) and [ADR-0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md).
