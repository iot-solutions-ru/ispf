# ADR-0080: Oracle metadata certification (amends 0037)

## Status

**Accepted** (2026-11-20) — policy and certification charter. Implementation **Not started**.

Amends [0037-relational-core-portability](0037-relational-core-portability.md). Oracle is scheduled after the MySQL B1 gate in [0079](0079-mysql-metadata-certification.md). This ADR does not grant production support.

## Context

ISPF has an `OracleDialect`, but no Oracle Flyway pack or certified metadata path. External Oracle through `DATA_SOURCE` is an integration path, not evidence that `ISPF_DB` can run on Oracle.

Oracle version, edition, JDBC driver, schema behavior, locking, and identifier rules must be named and tested; generic JDBC compatibility is not certification.

## Decision

- Scope the first gate to **B1 metadata** on a named Oracle database version/edition and JDBC driver.
- Keep journal and variable history outside the claim unless a later B2 decision and gate cover them.
- Start implementation only **after** MySQL passes its B1 gate in ADR-0079.
- Keep `Prod: No`. `UncertifiedRelationalEngineBootGuard` already refuses boot for `oracle`; retain that fail-closed behavior until the complete gate passes.
- Require a greenfield Flyway pack, fixed bundle import, concurrent audit verification, lease/failover checks, and explicit unsupported-feature failures before promotion.
- Treat containers, free editions, or a developer lab as lab evidence only, never as certification for another named production edition.

No Flyway SQL is authorized or supplied by this policy ADR.

## Consequences

- Oracle remains unavailable as the production metadata database.
- Sales and documentation must state the exact B1 scope and named database/driver combination.
- Engine changes remain greenfield database plus bundle import, not in-place migration.

## Related

- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [0079-mysql-metadata-certification](0079-mysql-metadata-certification.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
