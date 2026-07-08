# MS SQL Server migration pack (ADR-0037)

Enterprise POC target. **Greenfield only:** one `V1__baseline.sql` when certified.

Until `V1__baseline.sql` exists, do **not** set `ISPF_METADATA_DB_KIND=mssql` in production.

Runtime SQL portability is via `MssqlDialect` (job queue `READPAST`, bucket aggregation, schema DDL). Config migration between engines: new empty DB + platform bundle import.

See [STORAGE_PORTABILITY_INVENTORY.md](../../../../../../../../docs/en/storage-portability-inventory.md) and [ADR-0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md).
