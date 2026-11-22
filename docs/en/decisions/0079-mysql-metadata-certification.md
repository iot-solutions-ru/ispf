# ADR-0079: MySQL metadata certification (amends 0037)

## Status

**Accepted** (2026-11-20) — policy and certification charter. **Implementation: Phase 1 lab started** (2026-11-22). **Prod: No** — boot still refuses `ISPF_METADATA_DB_KIND=mysql`.

Amends [0037-relational-core-portability](0037-relational-core-portability.md). MySQL 8.x is the next enterprise metadata target after MSSQL B1 lab-green / Prod Yes on the Evaluation cell in [0078](0078-mssql-metadata-certification.md). This ADR does not grant production support.

## Context

ISPF has a `MysqlDialect`, but no MySQL Flyway pack or certified metadata path. External MySQL through `DATA_SOURCE` is an integration path, not evidence that `ISPF_DB` can run on MySQL.

MySQL certification does **not** imply MariaDB certification. They have distinct SQL, JDBC, locking, and operational behavior and require separate evidence. Config `mariadb` and product-name MariaDB fail closed (not aliased to MySQL).

## Decision

- Scope the first gate to **B1 metadata** on a named MySQL 8.x version/edition (prefer **8.4 LTS**; 8.0 acceptable for lab).
- Keep journal and variable history outside the claim unless a later B2 decision and gate cover them. **No** MSSQL-style single-engine bypass in B1 fences.
- **Lab Phase 1** may proceed after MSSQL B1 is Prod Yes / lab-green. The MySQL **named-SKU Prod Yes** gate is separate and still open.
- Keep `Prod: No`. `UncertifiedRelationalEngineBootGuard` / `MetadataDialectBootPolicy.assertEngineCertified` refuse boot for `mysql` until the complete gate passes. `assertMysqlB1Fences` is pre-wired (unit-tested) for the pack PR.
- Require a greenfield Flyway pack, fixed bundle import, concurrent audit verification, lease/failover checks, and explicit unsupported-feature failures before promotion.
- Treat Testcontainers or a developer lab as lab evidence only, never as production certification.
- Do not reuse a MySQL result for MariaDB. A MariaDB claim requires its own charter, pack, named version, and gate.
- Do **not** add `mysql-connector-j` (GPL) to any classpath until counsel signs a driver choice ([0016-agpl-dual-licensing](0016-agpl-dual-licensing.md)). Pack / generator / lockstep CI wait for a follow-on PR that amends this ADR to authorize lab Flyway SQL.

No Flyway SQL is authorized or supplied by this policy ADR yet.

## Consequences

- MySQL remains unavailable as the production metadata database.
- Sales and documentation must name **MySQL 8.x B1 metadata** and must not say “MySQL/MariaDB”.
- Engine changes remain greenfield database plus bundle import, not in-place migration.
- Oracle ([0080](0080-oracle-metadata-certification.md)) starts only after MySQL B1 Prod Yes.

## Related

- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [0080-oracle-metadata-certification](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
