# PostgreSQL Flyway pack

**Greenfield + incremental (post-squash):**

1. `V1__baseline.sql` — full platform schema after the 2026-07-07 squash of legacy V1–V70.
2. `V71__*.sql` … onward — normal Flyway upgrades on existing databases.

There is **no** upgrade path that replays the removed pre-squash V2–V70 scripts. Databases that still have that history must run `deploy/vps-flyway-repair.sh` once (reconcile `flyway_schema_history` to V1), then apply `V71+`.

New install: empty database → Flyway applies `V1__baseline.sql` and all later versions → import solution bundles for configuration.

Policy: [ADR-0037](../../../../../../../../docs/en/decisions/0037-relational-core-portability.md), [ADR-0064](../../../../../../../../docs/en/decisions/0064-release-compatibility-lts.md).
