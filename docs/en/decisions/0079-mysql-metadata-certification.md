# ADR-0079: MySQL metadata certification (amends 0037)

## Status

**Accepted** (2026-11-20) — policy and certification charter. Implementation **Not started**.

Amends [0037-relational-core-portability](0037-relational-core-portability.md). MySQL 8.x is the next enterprise metadata target after the MSSQL named-SKU gate in [0078](0078-mssql-metadata-certification.md). This ADR does not grant production support.

## Context

ISPF has a `MysqlDialect`, but no MySQL Flyway pack or certified metadata path. External MySQL through `DATA_SOURCE` is an integration path, not evidence that `ISPF_DB` can run on MySQL.

MySQL certification does **not** imply MariaDB certification. They have distinct SQL, JDBC, locking, and operational behavior and require separate evidence.

## Decision

- Scope the first gate to **B1 metadata** on a named MySQL 8.x version/edition.
- Keep journal and variable history outside the claim unless a later B2 decision and gate cover them.
- Start implementation only **after** MSSQL passes the named-SKU gate in ADR-0078. An MSSQL **policy waiver** (unsigned B1 envelope) is **not** that gate.
- Keep `Prod: No`. `UncertifiedRelationalEngineBootGuard` already refuses boot for `mysql`; retain that fail-closed behavior until the complete gate passes.
- Require a greenfield Flyway pack, fixed bundle import, concurrent audit verification, lease/failover checks, and explicit unsupported-feature failures before promotion.
- Treat Testcontainers or a developer lab as lab evidence only, never as production certification.
- Do not reuse a MySQL result for MariaDB. A MariaDB claim requires its own charter, pack, named version, and gate.

No Flyway SQL is authorized or supplied by this policy ADR.

## Consequences

- MySQL remains unavailable as the production metadata database.
- Sales and documentation must name **MySQL 8.x B1 metadata** and must not say “MySQL/MariaDB”.
- Engine changes remain greenfield database plus bundle import, not in-place migration.

## Related

- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [0080-oracle-metadata-certification](0080-oracle-metadata-certification.md)
- [mysql-certification-epic](../mysql-certification-epic.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
