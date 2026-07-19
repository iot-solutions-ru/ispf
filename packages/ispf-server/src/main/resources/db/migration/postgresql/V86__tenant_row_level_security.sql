-- BL-155 follow-up: PostgreSQL Row Level Security for tenant path isolation (A≠B).
-- GUC session vars (set by app on connection checkout):
--   app.tenant_bypass = 'on'|'off'  (default unset → COALESCE to 'on' = allow)
--   app.tenant_id     = tenant id string or ''
-- H2: Flyway placeholders wrap the body in a block comment (see FlywayDialectConfiguration).
--
-- TimescaleDB columnstore/compressed hypertables reject ENABLE ROW LEVEL SECURITY
-- (SQLSTATE 0A000). Historian tables may skip RLS; path isolation remains in the app layer.

${rls_block_start}
CREATE OR REPLACE FUNCTION ispf_tenant_path_visible(p text)
RETURNS boolean
LANGUAGE sql
STABLE
AS $ispf_rls$
  SELECT COALESCE(current_setting('app.tenant_bypass', true), 'on') = 'on'
      OR (
          nullif(current_setting('app.tenant_id', true), '') IS NOT NULL
          AND (
              p = 'root'
              OR p = 'root.tenant'
              OR p = 'root.tenant.' || current_setting('app.tenant_id', true)
              OR p LIKE 'root.tenant.' || current_setting('app.tenant_id', true) || '.%'
          )
      );
$ispf_rls$;

CREATE OR REPLACE FUNCTION ispf_enable_tenant_rls(p_table regclass, p_path_column text)
RETURNS void
LANGUAGE plpgsql
AS $ispf_rls_enable$
BEGIN
  EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', p_table);
  EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', p_table);
  EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_all ON %s', p_table);
  EXECUTE format(
    'CREATE POLICY tenant_isolation_all ON %s FOR ALL USING (ispf_tenant_path_visible(%I)) WITH CHECK (ispf_tenant_path_visible(%I))',
    p_table, p_path_column, p_path_column
  );
EXCEPTION
  WHEN feature_not_supported THEN
    RAISE NOTICE 'Skipping RLS on % (feature not supported, e.g. Timescale columnstore): %',
      p_table, SQLERRM;
  WHEN OTHERS THEN
    IF SQLSTATE = '0A000' THEN
      RAISE NOTICE 'Skipping RLS on % (SQLSTATE 0A000): %', p_table, SQLERRM;
    ELSE
      RAISE;
    END IF;
END;
$ispf_rls_enable$;

SELECT ispf_enable_tenant_rls('object_nodes'::regclass, 'path');
SELECT ispf_enable_tenant_rls('object_variables'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('object_acl_entries'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('event_history'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('variable_samples'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('object_config_audit'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('object_edit_leases'::regclass, 'path_prefix');
SELECT ispf_enable_tenant_rls('alarm_shelves'::regclass, 'object_path');
SELECT ispf_enable_tenant_rls('alarm_shelf_requests'::regclass, 'object_path');
${rls_block_end}
