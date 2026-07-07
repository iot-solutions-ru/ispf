# PostgreSQL migration pack (ADR-0037)

**Greenfield only:** single `V1__baseline.sql` — full platform schema for new installations.

Incremental scripts V1–V70 were squashed (2026-07-07). There is **no Flyway upgrade path** from legacy versioned migrations; existing databases must run `deploy/vps-flyway-repair.sh` once after rollout to reconcile `flyway_schema_history`.

New install: empty database → Flyway applies `V1__baseline.sql` → import solution bundles for configuration.

See [ADR-0037](../../../../../../../../docs/decisions/0037-relational-core-portability.md).
