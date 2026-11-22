# ADR-0080: Oracle metadata certification (amends 0037)

## Status

**Accepted** (2026-11-20) — policy and certification charter. **Implementation: Phase 1 lab started** (2026-11-22). **Prod: No** — boot still refuses `ISPF_METADATA_DB_KIND=oracle`.

Amends [0037-relational-core-portability](0037-relational-core-portability.md). The Oracle B1 pack is scheduled after the MySQL B1 gate in [0079](0079-mysql-metadata-certification.md). This ADR does not grant production support.

## Context

ISPF has an `OracleDialect`, but no Oracle Flyway pack or certified metadata path. External Oracle through `DATA_SOURCE` is an integration path, not evidence that `ISPF_DB` can run on Oracle.

Oracle version, edition, JDBC driver, schema behavior, locking, and identifier rules must be named and tested; generic JDBC compatibility is not certification. Two rules already shape the dialect: a quoted Oracle identifier cannot contain a double quote or NUL (there is no escape), and an Oracle schema is a database user, not a `CREATE SCHEMA` object.

## Decision

- Scope the first gate to **B1 metadata** on a named Oracle database version/edition and JDBC driver (candidates: **19c** or **23ai**, both long-term releases). Oracle Database Free is lab evidence only.
- Keep journal and variable history outside the claim unless a later B2 decision and gate cover them. **No** MSSQL-style single-engine bypass in B1 fences.
- **Lab Phase 1** started on 2026-11-22 by owner decision, ahead of MySQL B1 Prod Yes. Phase 1 is limited to dialect honesty and pre-wired fences. The Oracle B1 pack (Flyway SQL, JDBC driver, TCK) and any Prod Yes still wait for MySQL B1 Prod Yes in ADR-0079.
- Keep `Prod: No`. `UncertifiedRelationalEngineBootGuard` / `MetadataDialectBootPolicy.assertEngineCertified` refuse boot for `oracle` until the complete gate passes. `assertOracleB1Fences` / `OracleCertificationBootGuard` are pre-wired (unit-tested) for the pack PR: no row isolation, no `HARD` tenant mode, no dual-write, no cluster, no JDBC history/journal on `ISPF_DB`.
- Require a greenfield Flyway pack, fixed bundle import, concurrent audit verification, lease/failover checks, and explicit unsupported-feature failures before promotion.
- Treat containers, free editions, or a developer lab as lab evidence only, never as certification for another named production edition.
- Do **not** add an Oracle JDBC driver (`ojdbc*`, Oracle license terms) to any classpath until counsel signs a driver choice ([0016b — AGPL/dual-licensing](0016-agpl-dual-licensing.md)). Pack / generator / lockstep CI wait for a follow-on PR that amends this ADR to authorize lab Flyway SQL.

No Flyway SQL is authorized or supplied by this policy ADR yet.

## Consequences

- Oracle remains unavailable as the production metadata database.
- Sales and documentation must state the exact B1 scope and named database/driver combination.
- Engine changes remain greenfield database plus bundle import, not in-place migration.
- Starting Oracle Phase 1 early does not change the order of the packs: MySQL B1 Prod Yes still comes before the Oracle B1 pack.

## Related

- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0078-mssql-metadata-certification](0078-mssql-metadata-certification.md)
- [0079-mysql-metadata-certification](0079-mysql-metadata-certification.md)
- [oracle-certification-epic](../oracle-certification-epic.md)
- [storage-portability-inventory](../storage-portability-inventory.md)
