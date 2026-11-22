# MySQL migration pack (ADR-0037 / ADR-0079)

**Phase 1 lab started. Not certified. Prod: No.**

Boot refuses `ISPF_METADATA_DB_KIND=mysql` until a Flyway pack and certification gate exist
(`UncertifiedRelationalEngineBootGuard` / `MetadataDialectBootPolicy.assertEngineCertified`).
`assertMysqlB1Fences` is pre-wired for the pack PR (no single-engine bypass).

External JDBC `DATA_SOURCE` for reports remains supported. MariaDB is **not** this pack
(config `mariadb` and product-name MariaDB fail closed).

Charter: [0079-mysql-metadata-certification](../../../../../../../../docs/en/decisions/0079-mysql-metadata-certification.md).  
Epic: [mysql-certification-epic](../../../../../../../../docs/en/mysql-certification-epic.md).

No `V1__baseline.sql` in this directory yet — ADR-0079 has not authorized lab Flyway SQL;
generator / lockstep / JDBC driver wait for a follow-on PR after counsel signs the driver.
