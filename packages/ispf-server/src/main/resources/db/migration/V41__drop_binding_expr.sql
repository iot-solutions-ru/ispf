-- ADR-0017 / Phase 17.2: binding rules only; legacy column removed.
-- Fresh installs: column absent from V1. Existing DBs: drop after recreate or via this migration.
ALTER TABLE object_variables DROP COLUMN IF EXISTS binding_expr;
