# MySQL migration pack (ADR-0037 / ADR-0079)

Planned enterprise target **after** MSSQL named-SKU gate. **Not certified.**

Boot refuses `ISPF_METADATA_DB_KIND=mysql` until a Flyway pack and certification gate exist
(`UncertifiedRelationalEngineBootGuard`). External JDBC `DATA_SOURCE` for reports remains supported.

Charter: [0079-mysql-metadata-certification](../../../../../../../../docs/en/decisions/0079-mysql-metadata-certification.md).
MariaDB is **not** implied by this charter.
