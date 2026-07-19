-- BL-155 follow-up: PostgreSQL Row Level Security for tenant path isolation (A≠B).
-- GUC session vars (set by app on connection checkout):
--   app.tenant_bypass = 'on'|'off'  (default unset → COALESCE to 'on' = allow)
--   app.tenant_id     = tenant id string or ''
-- H2: Flyway placeholders wrap the body in a block comment (see FlywayDialectConfiguration).

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

-- object_nodes (path)
ALTER TABLE object_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_nodes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON object_nodes;
CREATE POLICY tenant_isolation_all ON object_nodes
    FOR ALL
    USING (ispf_tenant_path_visible(path))
    WITH CHECK (ispf_tenant_path_visible(path));

-- object_variables (object_path)
ALTER TABLE object_variables ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_variables FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON object_variables;
CREATE POLICY tenant_isolation_all ON object_variables
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- object_acl_entries (object_path)
ALTER TABLE object_acl_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_acl_entries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON object_acl_entries;
CREATE POLICY tenant_isolation_all ON object_acl_entries
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- event_history (object_path)
ALTER TABLE event_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_history FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON event_history;
CREATE POLICY tenant_isolation_all ON event_history
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- variable_samples (object_path)
ALTER TABLE variable_samples ENABLE ROW LEVEL SECURITY;
ALTER TABLE variable_samples FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON variable_samples;
CREATE POLICY tenant_isolation_all ON variable_samples
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- object_config_audit (object_path)
ALTER TABLE object_config_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_config_audit FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON object_config_audit;
CREATE POLICY tenant_isolation_all ON object_config_audit
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- object_edit_leases uses path_prefix (not object_path)
ALTER TABLE object_edit_leases ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_edit_leases FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON object_edit_leases;
CREATE POLICY tenant_isolation_all ON object_edit_leases
    FOR ALL
    USING (ispf_tenant_path_visible(path_prefix))
    WITH CHECK (ispf_tenant_path_visible(path_prefix));

-- alarm_shelves (object_path)
ALTER TABLE alarm_shelves ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_shelves FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON alarm_shelves;
CREATE POLICY tenant_isolation_all ON alarm_shelves
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));

-- alarm_shelf_requests (object_path)
ALTER TABLE alarm_shelf_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE alarm_shelf_requests FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_all ON alarm_shelf_requests;
CREATE POLICY tenant_isolation_all ON alarm_shelf_requests
    FOR ALL
    USING (ispf_tenant_path_visible(object_path))
    WITH CHECK (ispf_tenant_path_visible(object_path));
${rls_block_end}
