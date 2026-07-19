# H2 migration pack (ADR-0037)

H2 tests and local profile use **PostgreSQL compatibility mode** (`MODE=PostgreSQL`).

Flyway loads:

1. `db/migration/h2/` — H2-specific overrides (this folder; add scripts only when PG pack is incompatible)
2. `db/migration/postgresql/V1__baseline.sql` — full platform schema (shared)

PostgreSQL-only body of `V86__tenant_row_level_security.sql` is wrapped in Flyway
placeholders (`${rls_block_start}` / `${rls_block_end}`) so H2 receives a block comment
while PostgreSQL executes RLS DDL (`FlywayDialectConfiguration`).
