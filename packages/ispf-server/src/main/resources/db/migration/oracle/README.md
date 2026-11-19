# Oracle migration pack (ADR-0037 / ADR-0080)

Planned enterprise target **after** MySQL B1. **Not certified.**

Boot refuses `ISPF_METADATA_DB_KIND=oracle` until a Flyway pack and certification gate exist
(`UncertifiedRelationalEngineBootGuard`). External JDBC `DATA_SOURCE` for reports remains supported.

Charter: [0080-oracle-metadata-certification](../../../../../../../../docs/en/decisions/0080-oracle-metadata-certification.md).
