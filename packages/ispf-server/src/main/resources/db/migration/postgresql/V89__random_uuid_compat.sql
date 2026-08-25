-- Dual-dialect compatibility: H2 scripts use RANDOM_UUID(); PostgreSQL needs an alias.
-- gen_random_uuid() is built-in since PostgreSQL 13.
CREATE OR REPLACE FUNCTION public.random_uuid()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $$
    SELECT gen_random_uuid();
$$;
