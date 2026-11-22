# Oracle migration pack (ADR-0037 / ADR-0080)

**Phase 1 lab started. Not certified. Prod: No.**

Boot refuses `ISPF_METADATA_DB_KIND=oracle` until a Flyway pack and certification gate exist
(`UncertifiedRelationalEngineBootGuard` / `MetadataDialectBootPolicy.assertEngineCertified`).
`assertOracleB1Fences` / `OracleCertificationBootGuard` are pre-wired for the pack PR (no single-engine bypass).

External JDBC `DATA_SOURCE` for reports remains supported.

Charter: [0080-oracle-metadata-certification](../../../../../../../../docs/en/decisions/0080-oracle-metadata-certification.md).  
Epic: [oracle-certification-epic](../../../../../../../../docs/en/oracle-certification-epic.md).

No `V1__baseline.sql` in this directory yet — ADR-0080 has not authorized lab Flyway SQL.
No Oracle JDBC driver (`ojdbc*`) is on any classpath until counsel signs the driver license.
MySQL B1 Prod Yes is signed (ADR-0079), so the Oracle B1 pack may start.
