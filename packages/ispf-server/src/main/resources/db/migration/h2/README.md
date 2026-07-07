# H2 migration pack (ADR-0037)

H2 tests and local profile use **PostgreSQL compatibility mode** (`MODE=PostgreSQL`).

Flyway loads:

1. `db/migration/h2/` — H2-specific overrides (this folder; add scripts only when PG pack is incompatible)
2. `db/migration/postgresql/V1__baseline.sql` — full platform schema (shared)

No overrides are required today; the postgresql pack runs on H2 in PG compat mode.
