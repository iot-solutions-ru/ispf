-- Dual-dialect compatibility: H2 already provides RANDOM_UUID(); PostgreSQL needs an alias.
-- gen_random_uuid() is built-in since PostgreSQL 13.
-- H2: Flyway placeholders wrap the body in a block comment (see FlywayDialectConfiguration).

${rls_block_start}
CREATE OR REPLACE FUNCTION public.random_uuid()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $$
    SELECT gen_random_uuid();
$$;
${rls_block_end}
