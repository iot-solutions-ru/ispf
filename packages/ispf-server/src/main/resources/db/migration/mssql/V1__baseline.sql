-- ISPF MS SQL Server greenfield baseline (ADR-0078 B1)
-- Generated from postgresql V1 + V71+ (skipped V86 RLS, V89 random_uuid).
-- Empty database only. TIMESTAMP mapped to DATETIME2 (not rowversion).
-- Regenerate: python tools/mssql-baseline-generate.py

-- ===== V1__baseline.sql =====
-- ISPF platform relational schema (greenfield baseline, ADR-0037)
-- Squashed from incremental V1-V70; new installs only — no upgrade path from legacy versions.

-- ===== V1__init_schema.sql =====
CREATE TABLE object_nodes (
    id           VARCHAR(64)  PRIMARY KEY,
    path         VARCHAR(512) NOT NULL UNIQUE,
    type         VARCHAR(32)  NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    description  NVARCHAR(MAX),
    template_id  VARCHAR(128),
    created_at   DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    events_json  NVARCHAR(MAX),
    functions_json NVARCHAR(MAX)
);
GO


CREATE TABLE object_variables (
    id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    object_path VARCHAR(512) NOT NULL,
    name         VARCHAR(128) NOT NULL,
    schema_json  NVARCHAR(MAX)         NOT NULL,
    value_json   NVARCHAR(MAX),
    readable     BIT      NOT NULL DEFAULT 1,
    writable     BIT      NOT NULL DEFAULT 0,
    updated_at   DATETIME2,
    UNIQUE (object_path, name)
);
GO


CREATE TABLE event_history (
    id           VARCHAR(64)  PRIMARY KEY,
    object_path VARCHAR(512) NOT NULL,
    event_name   VARCHAR(128) NOT NULL,
    level        VARCHAR(16)  NOT NULL,
    payload_json NVARCHAR(MAX),
    occurred_at  DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_event_history_object ON event_history(object_path, occurred_at DESC);
GO

CREATE INDEX idx_object_variables_path ON object_variables(object_path);
GO


-- ===== V2__workflow_instances.sql =====
-- Workflow v2: persistent instances and operator work queue

CREATE TABLE workflow_instances (
    id              VARCHAR(64)  PRIMARY KEY,
    workflow_path   VARCHAR(512) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    current_node_id VARCHAR(128),
    assignee        VARCHAR(128),
    trigger_object_path VARCHAR(512),
    state_json      NVARCHAR(MAX),
    started_at      DATETIME2    NOT NULL,
    completed_at    DATETIME2,
    updated_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE workflow_user_tasks (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    task_node_id    VARCHAR(128) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    instructions    NVARCHAR(MAX),
    assignee_role   VARCHAR(64)  NOT NULL DEFAULT 'operator',
    status          VARCHAR(16)  NOT NULL,
    assignee        VARCHAR(128),
    created_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      DATETIME2,
    completed_at    DATETIME2
);
GO


CREATE INDEX idx_workflow_instances_path ON workflow_instances(workflow_path, status);
GO

CREATE INDEX idx_workflow_user_tasks_status ON workflow_user_tasks(status, created_at DESC);
GO

CREATE INDEX idx_workflow_user_tasks_instance ON workflow_user_tasks(instance_id);
GO


-- ===== V3__alert_rules.sql =====
CREATE TABLE alert_rules (
    id                 VARCHAR(64)  PRIMARY KEY,
    name               VARCHAR(256) NOT NULL,
    object_path       VARCHAR(512) NOT NULL,
    watch_variable     VARCHAR(128) NOT NULL,
    condition_expr     NVARCHAR(MAX)         NOT NULL,
    event_name         VARCHAR(128) NOT NULL,
    payload_variable   VARCHAR(128),
    enabled            BIT      NOT NULL DEFAULT 1,
    edge_trigger       BIT      NOT NULL DEFAULT 1,
    last_condition_met BIT,
    created_at         DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_alert_rules_object ON alert_rules(object_path, watch_variable);
GO


-- ===== V4__event_correlators.sql =====
CREATE TABLE event_correlators (
    id                 VARCHAR(64)  PRIMARY KEY,
    name               VARCHAR(256) NOT NULL,
    object_path       VARCHAR(512),
    event_name         VARCHAR(128) NOT NULL,
    window_seconds     INT          NOT NULL DEFAULT 0,
    min_occurrences    INT          NOT NULL DEFAULT 1,
    cooldown_seconds   INT          NOT NULL DEFAULT 60,
    action_type        VARCHAR(32)  NOT NULL,
    action_target      VARCHAR(512) NOT NULL,
    enabled            BIT      NOT NULL DEFAULT 1,
    last_triggered_at  DATETIME2,
    created_at         DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_event_correlators_match
    ON event_correlators(event_name, enabled);
GO


CREATE TABLE correlator_hits (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    correlator_id  VARCHAR(64)  NOT NULL,
    object_path   VARCHAR(512) NOT NULL,
    occurred_at    DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_correlator_hits_window
    ON correlator_hits(correlator_id, occurred_at DESC);
GO


-- ===== V5__correlator_patterns.sql =====
-- Multi-event correlator patterns (COUNT | SEQUENCE)

IF COL_LENGTH('event_correlators', 'pattern_type') IS NULL IF COL_LENGTH('event_correlators', 'pattern_type') IS NULL ALTER TABLE event_correlators ADD pattern_type VARCHAR(32) NOT NULL DEFAULT 'COUNT';
GO


IF COL_LENGTH('event_correlators', 'second_event_name') IS NULL IF COL_LENGTH('event_correlators', 'second_event_name') IS NULL ALTER TABLE event_correlators ADD second_event_name VARCHAR(128);
GO


IF COL_LENGTH('correlator_hits', 'event_name') IS NULL IF COL_LENGTH('correlator_hits', 'event_name') IS NULL ALTER TABLE correlator_hits ADD event_name VARCHAR(128);
GO


-- ===== V10__application_platform.sql =====
CREATE TABLE applications (
    app_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    table_prefix VARCHAR(64),
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE application_data_migrations (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    version VARCHAR(64) NOT NULL,
    script_id VARCHAR(128) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    applied_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_migration UNIQUE (app_id, version, script_id)
);
GO


CREATE TABLE application_functions (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    object_path VARCHAR(512) NOT NULL,
    function_name VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL DEFAULT '1',
    source_type VARCHAR(32) NOT NULL,
    source_body NVARCHAR(MAX) NOT NULL,
    input_schema_json NVARCHAR(MAX) NOT NULL,
    output_schema_json NVARCHAR(MAX) NOT NULL,
    deployed_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_function UNIQUE (app_id, object_path, function_name, version)
);
GO


CREATE TABLE platform_schedules (
    schedule_id VARCHAR(128) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    enabled BIT NOT NULL DEFAULT 1,
    interval_ms BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    action_json NVARCHAR(MAX) NOT NULL,
    last_tick_at DATETIME2,
    last_error NVARCHAR(MAX),
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE model_definitions (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    definition_json NVARCHAR(MAX) NOT NULL,
    builtin BIT NOT NULL DEFAULT 0,
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE function_invoke_audit (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    correlation_id VARCHAR(64),
    object_path VARCHAR(512),
    function_name VARCHAR(128),
    app_id VARCHAR(64),
    success BIT NOT NULL,
    error_message VARCHAR(1024),
    invoked_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE workflow_cancel_journal (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    workflow_path VARCHAR(512) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    detail_json NVARCHAR(MAX),
    cancelled_by VARCHAR(128),
    cancelled_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


-- ===== V11__application_schema_seeds.sql =====
IF COL_LENGTH('applications', 'schema_name') IS NULL IF COL_LENGTH('applications', 'schema_name') IS NULL ALTER TABLE applications ADD schema_name VARCHAR(64);
GO


CREATE TABLE application_data_seeds (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    profile VARCHAR(64) NOT NULL,
    seed_id VARCHAR(128) NOT NULL,
    applied_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_seed UNIQUE (app_id, profile, seed_id)
);
GO


-- ===== V12__application_sql_bindings.sql =====
CREATE TABLE application_sql_bindings (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    object_path VARCHAR(512) NOT NULL,
    variable_name VARCHAR(128) NOT NULL,
    query_sql NVARCHAR(MAX) NOT NULL,
    refresh_mode VARCHAR(32) NOT NULL DEFAULT 'on_schedule',
    refresh_interval_ms BIGINT,
    value_field VARCHAR(128) DEFAULT 'value',
    trigger_object_path VARCHAR(512),
    trigger_function_name VARCHAR(128),
    enabled BIT NOT NULL DEFAULT 1,
    last_refreshed_at DATETIME2,
    deployed_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_sql_binding UNIQUE (app_id, object_path, variable_name)
);
GO


-- ===== V13__application_bundle_deployments.sql =====
CREATE TABLE application_bundle_deployments (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    bundle_version VARCHAR(64) NOT NULL,
    manifest_json NVARCHAR(MAX) NOT NULL,
    operator_manifest_json NVARCHAR(MAX),
    deployed_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BIT NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_bundle_version UNIQUE (app_id, bundle_version)
);
GO


CREATE INDEX idx_app_bundle_active ON application_bundle_deployments (app_id, is_active);
GO


-- ===== V14__application_reports.sql =====
CREATE TABLE application_reports (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id),
    report_id VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description NVARCHAR(MAX),
    query_sql NVARCHAR(MAX) NOT NULL,
    parameters_json NVARCHAR(MAX),
    columns_json NVARCHAR(MAX) NOT NULL,
    max_rows INTEGER NOT NULL DEFAULT 1000,
    deployed_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_report UNIQUE (app_id, report_id)
);
GO


-- ===== V15__platform_users.sql =====
CREATE TABLE platform_users (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    roles_json VARCHAR(255) NOT NULL,
    object_path VARCHAR(512) NOT NULL UNIQUE,
    enabled BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE TABLE platform_auth_sessions (
    token VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL REFERENCES platform_users(username) ON DELETE CASCADE,
    expires_at DATETIME2 NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_platform_auth_sessions_username ON platform_auth_sessions(username);
GO

CREATE INDEX idx_platform_auth_sessions_expires ON platform_auth_sessions(expires_at);
GO


-- ===== V16__user_auto_start_app.sql =====
IF COL_LENGTH('platform_users', 'auto_start_enabled') IS NULL IF COL_LENGTH('platform_users', 'auto_start_enabled') IS NULL ALTER TABLE platform_users ADD auto_start_enabled BIT NOT NULL DEFAULT 0;
GO


IF COL_LENGTH('platform_users', 'auto_start_app') IS NULL IF COL_LENGTH('platform_users', 'auto_start_app') IS NULL ALTER TABLE platform_users ADD auto_start_app VARCHAR(128);
GO


-- ===== V17__operator_app_ui.sql =====
CREATE TABLE operator_app_ui (
    app_id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    default_dashboard VARCHAR(512),
    dashboards_json NVARCHAR(MAX) NOT NULL,
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


-- ===== V18__variable_samples.sql =====
-- Time-series samples for platform object variables (telemetry historian).
-- PostgreSQL: optional Timescale hypertable вЂ” SELECT create_hypertable('variable_samples', 'sampled_at', if_not_exists => 1);

CREATE TABLE variable_samples (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    object_path   VARCHAR(512) NOT NULL,
    variable_name VARCHAR(128) NOT NULL,
    field_name    VARCHAR(128) NOT NULL DEFAULT 'value',
    sampled_at    DATETIME2    NOT NULL,
    value_double  DOUBLE PRECISION,
    value_text    VARCHAR(2048)
);
GO


CREATE INDEX idx_variable_samples_lookup
    ON variable_samples (object_path, variable_name, field_name, sampled_at DESC);
GO


CREATE INDEX idx_variable_samples_retention
    ON variable_samples (sampled_at);
GO


-- ===== V19__variable_history_metadata.sql =====
IF COL_LENGTH('object_variables', 'history_enabled') IS NULL IF COL_LENGTH('object_variables', 'history_enabled') IS NULL ALTER TABLE object_variables ADD history_enabled BIT NOT NULL DEFAULT 0;
GO


IF COL_LENGTH('object_variables', 'history_retention_days') IS NULL IF COL_LENGTH('object_variables', 'history_retention_days') IS NULL ALTER TABLE object_variables ADD history_retention_days INTEGER;
GO


-- Backfill known telemetry variables created before per-variable history metadata.
UPDATE object_variables
SET history_enabled = 1
WHERE name IN (
    'temperature',
    'sysUpTime',
    'hrMemorySize',
    'hrSystemProcesses',
    'hrSystemNumUsers',
    'ifNumber',
    'ifInOctets',
    'ifOutOctets',
    'hrProcessorLoad'
);
GO


-- ===== V20__platform_roles.sql =====
CREATE TABLE platform_roles (
    name VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    built_in BIT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


INSERT INTO platform_roles (name, display_name, description, built_in)
VALUES
    ('admin', 'admin', 'Full platform administration', 1),
    ('operator', 'operator', 'Operator HMI and read-only automation', 1);
GO


-- ===== V21__object_tree_sort_order.sql =====
IF COL_LENGTH('object_nodes', 'sort_order') IS NULL IF COL_LENGTH('object_nodes', 'sort_order') IS NULL ALTER TABLE object_nodes ADD sort_order INT NOT NULL DEFAULT 0;
GO


-- ===== V22__system_object_types.sql =====
UPDATE object_nodes SET type = 'PLATFORM' WHERE path = 'root.platform';
GO

UPDATE object_nodes SET type = 'DEVICES' WHERE path = 'root.platform.devices';
GO

UPDATE object_nodes SET type = 'DASHBOARDS' WHERE path = 'root.platform.dashboards';
GO

UPDATE object_nodes SET type = 'WORKFLOWS' WHERE path = 'root.platform.workflows';
GO

UPDATE object_nodes SET type = 'ALERT_RULES' WHERE path = 'root.platform.alert-rules';
GO

UPDATE object_nodes SET type = 'CORRELATORS' WHERE path = 'root.platform.correlators';
GO

UPDATE object_nodes SET type = 'APPLICATIONS' WHERE path = 'root.platform.applications';
GO

UPDATE object_nodes SET type = 'OPERATOR_APPS' WHERE path = 'root.platform.operator-apps';
GO

UPDATE object_nodes SET type = 'SECURITY' WHERE path = 'root.platform.security';
GO

UPDATE object_nodes SET type = 'USERS' WHERE path = 'root.platform.security.users';
GO

UPDATE object_nodes SET type = 'ROLES' WHERE path = 'root.platform.security.roles';
GO


UPDATE object_nodes SET type = 'ROLE' WHERE template_id = 'platform-role-v1';
GO

UPDATE object_nodes SET type = 'FUNCTION' WHERE template_id = 'application-function-v1';
GO

UPDATE object_nodes SET type = 'SCHEDULE' WHERE template_id = 'application-schedule-v1';
GO

UPDATE object_nodes SET type = 'BINDING' WHERE template_id = 'application-binding-v1';
GO

UPDATE object_nodes SET type = 'MIGRATION' WHERE template_id = 'application-migration-v1';
GO

UPDATE object_nodes SET type = 'SCREEN' WHERE template_id = 'operator-screen-v1';
GO


UPDATE object_nodes SET type = 'REPORTS' WHERE path LIKE '%.reports' AND type = 'CUSTOM';
GO

UPDATE object_nodes SET type = 'FUNCTIONS' WHERE path LIKE '%.functions' AND type = 'CUSTOM';
GO

UPDATE object_nodes SET type = 'SCHEDULES' WHERE path LIKE '%.schedules' AND type = 'CUSTOM';
GO

UPDATE object_nodes SET type = 'BINDINGS' WHERE path LIKE '%.bindings' AND type = 'CUSTOM';
GO

UPDATE object_nodes SET type = 'MIGRATIONS' WHERE path LIKE '%.migrations' AND type = 'CUSTOM';
GO

UPDATE object_nodes SET type = 'SCREENS' WHERE path LIKE '%.screens' AND type = 'CUSTOM';
GO


-- ===== V23__platform_leader_locks.sql =====
CREATE TABLE platform_leader_locks (
    lock_name   VARCHAR(128) PRIMARY KEY,
    holder_id   VARCHAR(64)  NOT NULL,
    expires_at  DATETIME2    NOT NULL
);
GO


-- ===== V25__object_acl.sql =====
CREATE TABLE object_acl_entries (
    id              UNIQUEIDENTIFIER PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL,
    principal_type  VARCHAR(16)  NOT NULL,
    principal_id    VARCHAR(128) NOT NULL,
    permission      VARCHAR(16)  NOT NULL,
    created_at      DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_object_acl_path ON object_acl_entries (object_path);
GO

CREATE UNIQUE INDEX uq_object_acl_grant
    ON object_acl_entries (object_path, principal_type, principal_id, permission);
GO


-- ===== V26__federation_peers.sql =====
CREATE TABLE federation_peers (
    id           UNIQUEIDENTIFIER PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    base_url     VARCHAR(512) NOT NULL,
    auth_token   VARCHAR(512),
    path_prefix  VARCHAR(256) NOT NULL DEFAULT '',
    enabled      BIT      NOT NULL DEFAULT 1,
    description  VARCHAR(1024),
    created_at   DATETIME2    NOT NULL,
    updated_at   DATETIME2    NOT NULL
);
GO


CREATE UNIQUE INDEX uq_federation_peer_name ON federation_peers (name);
GO


-- ===== V27__platform_tenants.sql =====
CREATE TABLE platform_tenants (
    tenant_id    VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    enabled      BIT      NOT NULL DEFAULT 1,
    created_at   DATETIME2    NOT NULL,
    updated_at   DATETIME2    NOT NULL
);
GO


IF COL_LENGTH('platform_users', 'tenant_id') IS NULL IF COL_LENGTH('platform_users', 'tenant_id') IS NULL ALTER TABLE platform_users ADD tenant_id VARCHAR(64);
GO


CREATE INDEX idx_platform_users_tenant ON platform_users (tenant_id);
GO


-- ===== V28__snmp_rate_variable_history.sql =====
-- Enable historian for SNMP rate variables derived from Counter32 bindings.
UPDATE object_variables
SET history_enabled = 1
WHERE name IN ('ifInOctetsRate', 'ifOutOctetsRate');
GO


-- ===== V29__federation_peer_auth.sql =====
IF COL_LENGTH('federation_peers', 'connection_mode') IS NULL IF COL_LENGTH('federation_peers', 'connection_mode') IS NULL ALTER TABLE federation_peers ADD connection_mode VARCHAR(32) NOT NULL DEFAULT 'HTTP_PULL';
GO


IF COL_LENGTH('federation_peers', 'auth_mode') IS NULL IF COL_LENGTH('federation_peers', 'auth_mode') IS NULL ALTER TABLE federation_peers ADD auth_mode VARCHAR(32) NOT NULL DEFAULT 'STATIC_TOKEN';
GO


IF COL_LENGTH('federation_peers', 'token_expires_at') IS NULL IF COL_LENGTH('federation_peers', 'token_expires_at') IS NULL ALTER TABLE federation_peers ADD token_expires_at DATETIME2;
GO


IF COL_LENGTH('federation_peers', 'auth_username') IS NULL IF COL_LENGTH('federation_peers', 'auth_username') IS NULL ALTER TABLE federation_peers ADD auth_username VARCHAR(128);
GO


IF COL_LENGTH('federation_peers', 'auth_secret_enc') IS NULL IF COL_LENGTH('federation_peers', 'auth_secret_enc') IS NULL ALTER TABLE federation_peers ADD auth_secret_enc VARCHAR(1024);
GO


IF COL_LENGTH('federation_peers', 'auth_status') IS NULL IF COL_LENGTH('federation_peers', 'auth_status') IS NULL ALTER TABLE federation_peers ADD auth_status VARCHAR(32) NOT NULL DEFAULT 'OK';
GO


IF COL_LENGTH('federation_peers', 'last_auth_at') IS NULL IF COL_LENGTH('federation_peers', 'last_auth_at') IS NULL ALTER TABLE federation_peers ADD last_auth_at DATETIME2;
GO


IF COL_LENGTH('federation_peers', 'last_auth_error') IS NULL IF COL_LENGTH('federation_peers', 'last_auth_error') IS NULL ALTER TABLE federation_peers ADD last_auth_error VARCHAR(1024);
GO


-- ===== V30__federation_tunnel.sql =====
CREATE TABLE federation_inbound_registrations (
    id                      UNIQUEIDENTIFIER PRIMARY KEY,
    name                    VARCHAR(128) NOT NULL,
    registration_code_hash  VARCHAR(128) NOT NULL,
    path_prefix             VARCHAR(256) NOT NULL DEFAULT 'root.platform',
    expires_at              DATETIME2 NOT NULL,
    consumed_at             DATETIME2,
    created_by              VARCHAR(128),
    created_at              DATETIME2 NOT NULL,
    updated_at              DATETIME2 NOT NULL
);
GO


CREATE UNIQUE INDEX uq_federation_inbound_reg_name
    ON federation_inbound_registrations (name);
GO


CREATE TABLE federation_outbound_agents (
    id                  UNIQUEIDENTIFIER PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    hub_base_url        VARCHAR(512) NOT NULL,
    registration_code_enc VARCHAR(1024),
    session_token_enc   VARCHAR(1024),
    path_prefix         VARCHAR(256) NOT NULL DEFAULT 'root.platform',
    enabled             BIT NOT NULL DEFAULT 1,
    tunnel_status       VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    linked_peer_id      UNIQUEIDENTIFIER,
    last_error          VARCHAR(1024),
    last_connected_at   DATETIME2,
    created_at          DATETIME2 NOT NULL,
    updated_at          DATETIME2 NOT NULL
);
GO


CREATE UNIQUE INDEX uq_federation_outbound_agent_name
    ON federation_outbound_agents (name);
GO


CREATE TABLE federation_tunnel_sessions (
    session_id          VARCHAR(64) PRIMARY KEY,
    registration_id     UNIQUEIDENTIFIER,
    peer_id             UNIQUEIDENTIFIER NOT NULL,
    connected_at        DATETIME2 NOT NULL,
    last_ping_at        DATETIME2 NOT NULL,
    agent_version       VARCHAR(64),
    disconnected_at     DATETIME2
);
GO


CREATE INDEX idx_federation_tunnel_sessions_peer
    ON federation_tunnel_sessions (peer_id);
GO


-- ===== V31__platform_federation_secrets.sql =====
CREATE TABLE platform_federation_secrets (
    id           CHAR(1) NOT NULL PRIMARY KEY,
    secrets_key  VARCHAR(512) NOT NULL,
    created_at   DATETIME2    NOT NULL,
    updated_at   DATETIME2    NOT NULL,
    CONSTRAINT platform_federation_secrets_singleton CHECK (id = 'X')
);
GO


-- ===== V32__collaboration.sql =====
IF COL_LENGTH('object_nodes', 'revision') IS NULL IF COL_LENGTH('object_nodes', 'revision') IS NULL ALTER TABLE object_nodes ADD revision BIGINT NOT NULL DEFAULT 0;
GO

IF COL_LENGTH('object_nodes', 'last_changed_by') IS NULL IF COL_LENGTH('object_nodes', 'last_changed_by') IS NULL ALTER TABLE object_nodes ADD last_changed_by VARCHAR(128);
GO

IF COL_LENGTH('object_nodes', 'last_changed_at') IS NULL IF COL_LENGTH('object_nodes', 'last_changed_at') IS NULL ALTER TABLE object_nodes ADD last_changed_at DATETIME2;
GO


CREATE TABLE object_config_audit (
    id              VARCHAR(64)  PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL,
    change_type     VARCHAR(64)  NOT NULL,
    field           VARCHAR(256),
    actor           VARCHAR(128),
    occurred_at     DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision_before BIGINT,
    revision_after  BIGINT,
    summary_json    NVARCHAR(MAX)
);
GO


CREATE INDEX idx_object_config_audit_path
    ON object_config_audit (object_path, occurred_at DESC);
GO


CREATE TABLE object_edit_leases (
    id           VARCHAR(64)  PRIMARY KEY,
    path_prefix  VARCHAR(512) NOT NULL UNIQUE,
    holder       VARCHAR(128) NOT NULL,
    expires_at   DATETIME2    NOT NULL,
    created_at   DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_object_edit_leases_expires
    ON object_edit_leases (expires_at);
GO


CREATE TABLE platform_change_sets (
    id              VARCHAR(64)  PRIMARY KEY,
    title           VARCHAR(256) NOT NULL,
    author          VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    base_snapshot   NVARCHAR(MAX),
    ops_json        NVARCHAR(MAX)         NOT NULL,
    created_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_platform_change_sets_status
    ON platform_change_sets (status, updated_at DESC);
GO


-- ===== V33__reports_tree_first.sql =====
UPDATE object_nodes SET type = 'REPORTS' WHERE path = 'root.platform.reports';
GO


UPDATE object_nodes SET type = 'REPORT' WHERE template_id = 'report-v1';
GO


DELETE FROM object_variables
WHERE object_path IN (
    SELECT path FROM object_nodes
    WHERE path LIKE 'root.platform.applications.%.reports.%'
);
GO


DELETE FROM object_nodes
WHERE path LIKE 'root.platform.applications.%.reports.%';
GO


DELETE FROM object_nodes
WHERE path LIKE 'root.platform.applications.%.reports'
  AND type IN ('REPORTS', 'CUSTOM');
GO


-- ===== V34__report_templates.sql =====
CREATE TABLE report_templates (
    report_path VARCHAR(512) PRIMARY KEY,
    format      VARCHAR(16)  NOT NULL,
    content     VARBINARY(MAX) NOT NULL,
    updated_at  DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


-- ===== V35__object_nodes_type_index.sql =====
CREATE INDEX idx_object_nodes_type ON object_nodes (type);
GO

CREATE INDEX idx_object_nodes_path_prefix ON object_nodes (path);
GO


-- ===== V36__application_event_catalog.sql =====
CREATE TABLE application_event_catalog (
    app_id VARCHAR(64) NOT NULL REFERENCES applications(app_id) ON DELETE CASCADE,
    event_id VARCHAR(128) NOT NULL,
    roles_json NVARCHAR(MAX) NOT NULL DEFAULT '[]',
    payload_schema_json NVARCHAR(MAX),
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, event_id)
);
GO


CREATE INDEX idx_application_event_catalog_app ON application_event_catalog (app_id);
GO


-- ===== V37__ai_tool_audit.sql =====
CREATE TABLE ai_tool_audit (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tool_name VARCHAR(64) NOT NULL,
    app_id VARCHAR(128),
    actor VARCHAR(128),
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_id VARCHAR(64),
    model_id VARCHAR(128),
    context_pack_version VARCHAR(64),
    errors_json NVARCHAR(MAX),
    created_at DATETIME2 NOT NULL
);
GO


CREATE INDEX idx_ai_tool_audit_created_at ON ai_tool_audit (created_at DESC);
GO

CREATE INDEX idx_ai_tool_audit_app_id ON ai_tool_audit (app_id);
GO


-- ===== V38__agent_sessions.sql =====
CREATE TABLE agent_sessions (
    session_id   VARCHAR(64)  PRIMARY KEY,
    actor        VARCHAR(128) NOT NULL,
    root_path    VARCHAR(512) NOT NULL,
    title        VARCHAR(256) NOT NULL,
    run_state_json NVARCHAR(MAX),
    created_at   DATETIME2    NOT NULL,
    updated_at   DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_agent_sessions_actor_updated ON agent_sessions (actor, updated_at DESC);
GO


CREATE TABLE agent_turns (
    turn_id            VARCHAR(64) PRIMARY KEY,
    session_id         VARCHAR(64) NOT NULL,
    user_message       NVARCHAR(MAX),
    assistant_summary  NVARCHAR(MAX),
    status             VARCHAR(16) NOT NULL,
    steps_json         NVARCHAR(MAX),
    result_json        NVARCHAR(MAX),
    created_at         DATETIME2   NOT NULL,
    sort_order         INT         NOT NULL,
    CONSTRAINT fk_agent_turns_session FOREIGN KEY (session_id) REFERENCES agent_sessions(session_id) ON DELETE CASCADE
);
GO


CREATE INDEX idx_agent_turns_session_order ON agent_turns (session_id, sort_order);
GO


-- ===== V39__object_applied_models.sql =====
IF COL_LENGTH('object_nodes', 'applied_model_ids') IS NULL IF COL_LENGTH('object_nodes', 'applied_model_ids') IS NULL ALTER TABLE object_nodes ADD applied_model_ids NVARCHAR(MAX);
GO


UPDATE object_nodes
SET applied_model_ids = CONCAT('["', REPLACE(template_id, '"', '\"'), '"]')
WHERE template_id IS NOT NULL
  AND template_id <> ''
  AND (applied_model_ids IS NULL OR TRIM(applied_model_ids) = '');
GO


-- ===== V40__operator_app_ui_extras.sql =====
IF COL_LENGTH('operator_app_ui', 'ui_extras_json') IS NULL IF COL_LENGTH('operator_app_ui', 'ui_extras_json') IS NULL ALTER TABLE operator_app_ui ADD ui_extras_json NVARCHAR(MAX);
GO


-- ===== V41__drop_binding_expr.sql =====
-- 0010 / Phase 17.2: binding rules only; legacy column removed.
-- Fresh installs: column absent from V1. Existing DBs: drop after recreate or via this migration.
IF COL_LENGTH('object_variables', 'binding_expr') IS NOT NULL ALTER TABLE object_variables DROP COLUMN binding_expr;
GO


-- ===== V42__workflow_operator_app.sql =====
-- Snapshot operator app on work queue tasks (from workflow.operatorAppId at task creation)

IF COL_LENGTH('workflow_user_tasks', 'operator_app_id') IS NULL IF COL_LENGTH('workflow_user_tasks', 'operator_app_id') IS NULL ALTER TABLE workflow_user_tasks ADD operator_app_id VARCHAR(128);
GO


CREATE INDEX idx_workflow_user_tasks_operator_app
    ON workflow_user_tasks(operator_app_id, status, created_at DESC);
GO


-- ===== V43__platform_catalog_sort_order.sql =====
-- Default platform catalog folder order (matches production VPS layout).
UPDATE object_nodes SET sort_order = 0 WHERE path = 'root.platform.security';
GO

UPDATE object_nodes SET sort_order = 1 WHERE path = 'root.platform.devices';
GO

UPDATE object_nodes SET sort_order = 2 WHERE path = 'root.platform.alert-rules';
GO

UPDATE object_nodes SET sort_order = 3 WHERE path = 'root.platform.operator-apps';
GO

UPDATE object_nodes SET sort_order = 4 WHERE path = 'root.platform.dashboards';
GO

UPDATE object_nodes SET sort_order = 5 WHERE path = 'root.platform.relative-models';
GO

UPDATE object_nodes SET sort_order = 6 WHERE path = 'root.platform.absolute-models';
GO

UPDATE object_nodes SET sort_order = 7 WHERE path = 'root.platform.instance-types';
GO

UPDATE object_nodes SET sort_order = 8 WHERE path = 'root.platform.reports';
GO

UPDATE object_nodes SET sort_order = 9 WHERE path = 'root.platform.correlators';
GO

UPDATE object_nodes SET sort_order = 10 WHERE path = 'root.platform.workflows';
GO

UPDATE object_nodes SET sort_order = 11 WHERE path = 'root.platform.schedules';
GO

UPDATE object_nodes SET sort_order = 12 WHERE path = 'root.platform.data-sources';
GO

UPDATE object_nodes SET sort_order = 13 WHERE path = 'root.platform.bindings';
GO

UPDATE object_nodes SET sort_order = 14 WHERE path = 'root.platform.migrations';
GO

UPDATE object_nodes SET sort_order = 15 WHERE path = 'root.platform.federation';
GO

UPDATE object_nodes SET sort_order = 16 WHERE path = 'root.platform.applications';
GO

UPDATE object_nodes SET sort_order = 17 WHERE path = 'root.platform.instances';
GO

UPDATE object_nodes SET sort_order = 0 WHERE path = 'root.platform';
GO

UPDATE object_nodes SET sort_order = 1 WHERE path = 'root.tenant';
GO


-- ===== V44__correlator_hits_composite_index.sql =====
CREATE INDEX idx_correlator_hits_lookup
  ON correlator_hits (correlator_id, object_path, event_name, occurred_at DESC);
GO


-- ===== V45__system_intrinsic_models.sql =====
-- Remove system-intrinsic model catalog nodes (schemas are embedded in object instances).
DELETE FROM object_nodes
WHERE path IN (
    'root.platform.relative-models.data-source-v1',
    'root.platform.relative-models.schedule-v1',
    'root.platform.relative-models.sql-binding-v1',
    'root.platform.relative-models.migration-v1',
    'root.platform.relative-models.alert-rule-v1',
    'root.platform.relative-models.correlator-v1',
    'root.platform.relative-models.dashboard-v1',
    'root.platform.relative-models.report-v1',
    'root.platform.relative-models.workflow-v1'
);
GO


-- ===== V46__event_history_occurred_at_index.sql =====
CREATE INDEX idx_event_history_occurred_at ON event_history(occurred_at DESC);
GO


-- ===== V47__event_history_timescale.sql =====
-- Event journal (alert fires, API events). Append-only time series.
-- Timescale hypertable: create_hypertable('event_history', 'occurred_at') at startup when extension is present.

-- idx_event_history_occurred_at (V46) and idx_event_history_object (V1) support time-ordered queries.

-- ===== V48__binding_invoke_audit.sql =====
CREATE TABLE binding_invoke_audit (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    binding_kind VARCHAR(16) NOT NULL,
    object_path VARCHAR(512) NOT NULL,
    rule_id VARCHAR(128),
    rule_name VARCHAR(255),
    trigger_kind VARCHAR(32) NOT NULL,
    target_variable VARCHAR(128),
    success BIT NOT NULL,
    changed BIT NOT NULL DEFAULT 0,
    error_message VARCHAR(1024),
    duration_ms INTEGER,
    invoked_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_binding_invoke_audit_object_path ON binding_invoke_audit (object_path, invoked_at DESC);
GO

CREATE INDEX idx_binding_invoke_audit_invoked_at ON binding_invoke_audit (invoked_at DESC);
GO


-- ===== V49__object_binding_audit_flag.sql =====
IF COL_LENGTH('object_nodes', 'binding_audit_enabled') IS NULL IF COL_LENGTH('object_nodes', 'binding_audit_enabled') IS NULL ALTER TABLE object_nodes ADD binding_audit_enabled BIT NOT NULL DEFAULT 0;
GO


CREATE INDEX idx_object_nodes_binding_audit_enabled
    ON object_nodes (binding_audit_enabled);
GO


-- ===== V50__object_function_audit_flag.sql =====
IF COL_LENGTH('object_nodes', 'function_audit_enabled') IS NULL IF COL_LENGTH('object_nodes', 'function_audit_enabled') IS NULL ALTER TABLE object_nodes ADD function_audit_enabled BIT NOT NULL DEFAULT 0;
GO


CREATE INDEX idx_object_nodes_function_audit_enabled
    ON object_nodes (function_audit_enabled);
GO


-- ===== V51__journal_audit_details.sql =====
IF COL_LENGTH('function_invoke_audit', 'input_json') IS NULL IF COL_LENGTH('function_invoke_audit', 'input_json') IS NULL ALTER TABLE function_invoke_audit ADD input_json NVARCHAR(MAX);
GO

IF COL_LENGTH('function_invoke_audit', 'output_json') IS NULL IF COL_LENGTH('function_invoke_audit', 'output_json') IS NULL ALTER TABLE function_invoke_audit ADD output_json NVARCHAR(MAX);
GO


IF COL_LENGTH('binding_invoke_audit', 'detail_json') IS NULL IF COL_LENGTH('binding_invoke_audit', 'detail_json') IS NULL ALTER TABLE binding_invoke_audit ADD detail_json NVARCHAR(MAX);
GO


-- ===== V52__operator_agent_memory.sql =====
-- Long-lived knowledge per operator application (shared across operators of the app).
CREATE TABLE operator_agent_memory (
    memory_id     VARCHAR(64)  PRIMARY KEY,
    app_id        VARCHAR(128) NOT NULL,
    kind          VARCHAR(32)  NOT NULL,
    topic         VARCHAR(256) NOT NULL,
    content       NVARCHAR(MAX)         NOT NULL,
    source_actor  VARCHAR(128),
    source_turn_id VARCHAR(64),
    use_count     INT          NOT NULL DEFAULT 0,
    created_at    DATETIME2    NOT NULL,
    updated_at    DATETIME2    NOT NULL,
    CONSTRAINT uq_operator_agent_memory_app_topic UNIQUE (app_id, topic)
);
GO


CREATE INDEX idx_operator_agent_memory_app_updated ON operator_agent_memory (app_id, updated_at DESC);
GO


-- ===== V53__operator_app_documents.sql =====
CREATE TABLE operator_app_documents (
    doc_id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(128) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    description VARCHAR(512),
    content_text NVARCHAR(MAX) NOT NULL,
    byte_size BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL
);
GO


CREATE INDEX idx_operator_app_documents_app ON operator_app_documents (app_id);
GO


-- ===== V54__mimic_objects.sql =====
UPDATE object_nodes SET type = 'MIMICS' WHERE path = 'root.platform.mimics';
GO


UPDATE object_nodes SET type = 'MIMIC' WHERE template_id = 'mimic-v1';
GO


-- ===== V55__platform_user_time_zone.sql =====
IF COL_LENGTH('platform_users', 'time_zone') IS NULL IF COL_LENGTH('platform_users', 'time_zone') IS NULL ALTER TABLE platform_users ADD time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';
GO


-- ===== V56__variable_samples_observed_at.sql =====
-- ADR-0020: device measurement time (observed_at) vs platform ingest time (sampled_at).
IF COL_LENGTH('variable_samples', 'observed_at') IS NULL IF COL_LENGTH('variable_samples', 'observed_at') IS NULL ALTER TABLE variable_samples ADD observed_at DATETIME2;
GO


UPDATE variable_samples
SET observed_at = sampled_at
WHERE observed_at IS NULL;
GO


ALTER TABLE variable_samples ALTER COLUMN observed_at DATETIME2 NOT NULL;
GO


-- ===== V57__alarm_shelves.sql =====
CREATE TABLE alarm_shelves (
    id VARCHAR(64) PRIMARY KEY,
    object_path VARCHAR(512) NOT NULL,
    event_name VARCHAR(128) NOT NULL,
    alert_rule_path VARCHAR(512),
    shelved_by VARCHAR(128) NOT NULL,
    shelved_at DATETIME2 NOT NULL,
    expires_at DATETIME2,
    comment VARCHAR(1024),
    active BIT NOT NULL DEFAULT 1
);
GO


CREATE INDEX idx_alarm_shelves_active_lookup ON alarm_shelves (object_path, event_name, active);
GO


-- ===== V58__platform_driver_locks.sql =====
CREATE TABLE platform_driver_locks (
    device_path VARCHAR(512) PRIMARY KEY,
    holder_id   VARCHAR(64)  NOT NULL,
    expires_at  DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_platform_driver_locks_expires ON platform_driver_locks (expires_at);
GO


-- ===== V59__platform_cluster_replicas.sql =====
CREATE TABLE platform_cluster_replicas (
    replica_id         VARCHAR(64)  PRIMARY KEY,
    version            VARCHAR(32),
    environment        VARCHAR(64),
    java_version       VARCHAR(64),
    started_at         DATETIME2    NOT NULL,
    last_heartbeat_at  DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_platform_cluster_replicas_heartbeat ON platform_cluster_replicas (last_heartbeat_at);
GO


-- ===== V60__object_event_journal_flag.sql =====
IF COL_LENGTH('object_nodes', 'event_journal_enabled') IS NULL IF COL_LENGTH('object_nodes', 'event_journal_enabled') IS NULL ALTER TABLE object_nodes ADD event_journal_enabled BIT NOT NULL DEFAULT 0;
GO


CREATE INDEX idx_object_nodes_event_journal_enabled
    ON object_nodes (event_journal_enabled);
GO


-- ===== V61__model_to_blueprint.sql =====
-- Rename model domain to blueprint (ObjectType, catalog paths, persistence).

UPDATE object_nodes SET type = 'BLUEPRINT' WHERE type = 'MODEL';
GO


UPDATE object_nodes SET path = REPLACE(path, 'relative-models', 'relative-blueprints');
GO

UPDATE object_nodes SET path = REPLACE(path, 'absolute-models', 'absolute-blueprints');
GO

UPDATE object_nodes SET path = REPLACE(path, 'root.platform.models', 'root.platform.blueprints');
GO


IF COL_LENGTH('object_nodes', 'applied_blueprint_ids') IS NULL IF COL_LENGTH('object_nodes', 'applied_blueprint_ids') IS NULL ALTER TABLE object_nodes ADD applied_blueprint_ids NVARCHAR(MAX);
GO

UPDATE object_nodes
SET applied_blueprint_ids = applied_model_ids
WHERE applied_model_ids IS NOT NULL
  AND (applied_blueprint_ids IS NULL OR TRIM(applied_blueprint_ids) = '');
GO

IF COL_LENGTH('object_nodes', 'applied_model_ids') IS NOT NULL ALTER TABLE object_nodes DROP COLUMN applied_model_ids;
GO


EXEC sp_rename 'model_definitions', 'blueprint_definitions';
GO


UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"modelVersion"', '"blueprintVersion"');
GO


-- ===== V62__platform_jobs.sql =====
CREATE TABLE platform_jobs (
    job_id         UNIQUEIDENTIFIER         PRIMARY KEY,
    job_type       VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    priority       INT          NOT NULL DEFAULT 0,
    payload        VARCHAR(MAX) NOT NULL,
    result         VARCHAR(MAX),
    error_message  VARCHAR(4096),
    holder_id      VARCHAR(64),
    expires_at     DATETIME2,
    created_at     DATETIME2    NOT NULL,
    started_at     DATETIME2,
    completed_at   DATETIME2,
    created_by     VARCHAR(128)
);
GO


CREATE INDEX idx_platform_jobs_status_created ON platform_jobs (status, created_at);
GO

CREATE INDEX idx_platform_jobs_expires_at ON platform_jobs (expires_at);
GO


-- ===== V63__platform_cluster_replica_role.sql =====
IF COL_LENGTH('platform_cluster_replicas', 'replica_role') IS NULL IF COL_LENGTH('platform_cluster_replicas', 'replica_role') IS NULL ALTER TABLE platform_cluster_replicas ADD replica_role VARCHAR(16) NOT NULL DEFAULT 'all';
GO


-- ===== V64__platform_cluster_replica_profile.sql =====
IF COL_LENGTH('platform_cluster_replicas', 'replica_profile') IS NULL IF COL_LENGTH('platform_cluster_replicas', 'replica_profile') IS NULL ALTER TABLE platform_cluster_replicas ADD replica_profile VARCHAR(32) NOT NULL DEFAULT 'unified';
GO


IF COL_LENGTH('platform_cluster_replicas', 'replica_capabilities') IS NULL IF COL_LENGTH('platform_cluster_replicas', 'replica_capabilities') IS NULL ALTER TABLE platform_cluster_replicas ADD replica_capabilities VARCHAR(512) NOT NULL DEFAULT '';
GO


UPDATE platform_cluster_replicas
SET replica_profile = CASE replica_role
    WHEN 'api' THEN 'edge-api'
    WHEN 'worker' THEN 'compute'
    WHEN 'all' THEN 'unified'
    ELSE replica_role
END
WHERE replica_profile = 'unified';
GO


-- ===== V65__platform_cluster_replica_http_port.sql =====
IF COL_LENGTH('platform_cluster_replicas', 'http_port') IS NULL IF COL_LENGTH('platform_cluster_replicas', 'http_port') IS NULL ALTER TABLE platform_cluster_replicas ADD http_port INT;
GO


UPDATE platform_cluster_replicas
SET http_port = 8080
WHERE http_port IS NULL;
GO


-- ===== V66__platform_binding_periodic_rules.sql =====
CREATE TABLE platform_binding_periodic_rules (
    object_path   VARCHAR(512) NOT NULL,
    rule_id       VARCHAR(128) NOT NULL,
    periodic_ms   BIGINT       NOT NULL,
    enabled       BIT      NOT NULL DEFAULT 1,
    last_run_at   DATETIME2,
    next_run_at   DATETIME2  NOT NULL,
    PRIMARY KEY (object_path, rule_id)
);
GO


CREATE INDEX idx_binding_periodic_next_run
    ON platform_binding_periodic_rules (next_run_at);
GO


-- ===== V67__ai_tool_audit_metrics.sql =====
IF COL_LENGTH('ai_tool_audit', 'latency_ms') IS NULL IF COL_LENGTH('ai_tool_audit', 'latency_ms') IS NULL ALTER TABLE ai_tool_audit ADD latency_ms BIGINT;
GO

IF COL_LENGTH('ai_tool_audit', 'prompt_tokens') IS NULL IF COL_LENGTH('ai_tool_audit', 'prompt_tokens') IS NULL ALTER TABLE ai_tool_audit ADD prompt_tokens INT;
GO

IF COL_LENGTH('ai_tool_audit', 'completion_tokens') IS NULL IF COL_LENGTH('ai_tool_audit', 'completion_tokens') IS NULL ALTER TABLE ai_tool_audit ADD completion_tokens INT;
GO

IF COL_LENGTH('ai_tool_audit', 'turn_id') IS NULL IF COL_LENGTH('ai_tool_audit', 'turn_id') IS NULL ALTER TABLE ai_tool_audit ADD turn_id VARCHAR(64);
GO

IF COL_LENGTH('ai_tool_audit', 'step_no') IS NULL IF COL_LENGTH('ai_tool_audit', 'step_no') IS NULL ALTER TABLE ai_tool_audit ADD step_no INT;
GO

IF COL_LENGTH('ai_tool_audit', 'interaction_mode') IS NULL IF COL_LENGTH('ai_tool_audit', 'interaction_mode') IS NULL ALTER TABLE ai_tool_audit ADD interaction_mode VARCHAR(16);
GO

IF COL_LENGTH('ai_tool_audit', 'prompt_profile') IS NULL IF COL_LENGTH('ai_tool_audit', 'prompt_profile') IS NULL ALTER TABLE ai_tool_audit ADD prompt_profile VARCHAR(32);
GO


CREATE INDEX idx_ai_tool_audit_turn ON ai_tool_audit (app_id, turn_id, step_no);
GO


-- ===== V68__agent_session_documents.sql =====
CREATE TABLE agent_session_documents (
    doc_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    description VARCHAR(512),
    content_text NVARCHAR(MAX) NOT NULL,
    byte_size BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT fk_agent_session_documents_session
        FOREIGN KEY (session_id) REFERENCES agent_sessions(session_id) ON DELETE CASCADE
);
GO


CREATE INDEX idx_agent_session_documents_session ON agent_session_documents (session_id);
GO


-- ===== V69__dashboard_legacy_report_paths.sql =====
-- Rewrite legacy application-scoped report paths in dashboard layouts (pre tree-first V33).
-- skipped PG regexp_replace data rewrite (greenfield)

-- ===== V70__platform_tenant_quotas.sql =====
IF COL_LENGTH('platform_tenants', 'max_devices') IS NULL IF COL_LENGTH('platform_tenants', 'max_devices') IS NULL ALTER TABLE platform_tenants ADD max_devices INT;
GO

IF COL_LENGTH('platform_tenants', 'max_objects') IS NULL IF COL_LENGTH('platform_tenants', 'max_objects') IS NULL ALTER TABLE platform_tenants ADD max_objects INT;
GO


-- ===== V71__variable_acl_roles.sql =====
IF COL_LENGTH('object_variables', 'read_roles_json') IS NULL IF COL_LENGTH('object_variables', 'read_roles_json') IS NULL ALTER TABLE object_variables ADD read_roles_json NVARCHAR(MAX);
GO


IF COL_LENGTH('object_variables', 'write_roles_json') IS NULL IF COL_LENGTH('object_variables', 'write_roles_json') IS NULL ALTER TABLE object_variables ADD write_roles_json NVARCHAR(MAX);
GO


-- ===== V71__variable_acl_roles.sql =====
-- Per-variable ACL: optional role lists (JSON arrays). Empty/null = inherit object ACL.

IF COL_LENGTH('object_variables', 'read_roles_json') IS NULL IF COL_LENGTH('object_variables', 'read_roles_json') IS NULL ALTER TABLE object_variables ADD read_roles_json NVARCHAR(MAX);
GO


IF COL_LENGTH('object_variables', 'write_roles_json') IS NULL IF COL_LENGTH('object_variables', 'write_roles_json') IS NULL ALTER TABLE object_variables ADD write_roles_json NVARCHAR(MAX);
GO


-- ===== V72__audit_events.sql =====
-- Immutable security audit trail — BL-156

CREATE TABLE audit_events (
    id           VARCHAR(64)  PRIMARY KEY,
    category     VARCHAR(64)  NOT NULL,
    action       VARCHAR(128) NOT NULL,
    actor        VARCHAR(128),
    target_type  VARCHAR(64),
    target_id    VARCHAR(512),
    details_json NVARCHAR(MAX),
    occurred_at  DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_audit_events_occurred ON audit_events(occurred_at DESC);
GO

CREATE INDEX idx_audit_events_category ON audit_events(category, occurred_at DESC);
GO


-- ===== V73__mes_platform_catalog.sql =====
-- BL-164: reconcile MES catalog sort order (folders seeded by PlatformBootstrap / MesPlatformBootstrap)

UPDATE object_nodes SET sort_order = 12 WHERE path = 'root.platform.schedules';
GO

UPDATE object_nodes SET sort_order = 13 WHERE path = 'root.platform.data-sources';
GO

UPDATE object_nodes SET sort_order = 14 WHERE path = 'root.platform.bindings';
GO

UPDATE object_nodes SET sort_order = 15 WHERE path = 'root.platform.migrations';
GO

UPDATE object_nodes SET sort_order = 16 WHERE path = 'root.platform.federation';
GO

UPDATE object_nodes SET sort_order = 17 WHERE path = 'root.platform.applications';
GO

UPDATE object_nodes SET sort_order = 18 WHERE path = 'root.platform.instances';
GO


UPDATE object_nodes SET sort_order = 11 WHERE path = 'root.platform.mes';
GO


-- ===== V74__mfa_enrollments.sql =====
-- BL-153: persisted TOTP MFA enrollments

CREATE TABLE mfa_enrollments (
    username    VARCHAR(64)  PRIMARY KEY,
    secret      VARCHAR(128) NOT NULL,
    enrolled_at DATETIME2,
    created_at  DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_mfa_enrollments_enrolled ON mfa_enrollments(enrolled_at);
GO


-- ===== V75__platform_analytics_schedules.sql =====
-- BL-203: analytics calculation engine periodic schedule index
CREATE TABLE platform_analytics_schedules (
    tag_path VARCHAR(512) PRIMARY KEY,
    helper VARCHAR(64) NOT NULL,
    source_paths_json NVARCHAR(MAX) NOT NULL,
    window_bucket VARCHAR(16) NOT NULL,
    periodic_ms BIGINT NOT NULL DEFAULT 60000,
    on_change_enabled BIT NOT NULL DEFAULT 1,
    enabled BIT NOT NULL DEFAULT 1,
    next_run_at DATETIME2,
    last_tick_at DATETIME2,
    last_error NVARCHAR(MAX)
);
GO


CREATE INDEX idx_analytics_schedules_next_run
    ON platform_analytics_schedules (next_run_at);
GO


-- ===== V76__platform_event_frames.sql =====
-- BL-208: analytics event frames (shift / batch / downtime windows)
CREATE TABLE platform_event_frames (
    frame_id UNIQUEIDENTIFIER PRIMARY KEY,
    frame_type VARCHAR(32) NOT NULL,
    scope_path VARCHAR(512) NOT NULL,
    source_path VARCHAR(512),
    source_key VARCHAR(256),
    label VARCHAR(256) NOT NULL,
    started_at DATETIME2 NOT NULL,
    ended_at DATETIME2,
    downtime_minutes INTEGER NOT NULL DEFAULT 0,
    metadata_json NVARCHAR(MAX),
    created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_event_frames_scope_active
    ON platform_event_frames (scope_path, frame_type, ended_at);
GO


CREATE INDEX idx_event_frames_started
    ON platform_event_frames (started_at DESC);
GO


-- ===== V77__variable_telemetry_publish_mode.sql =====
IF COL_LENGTH('object_variables', 'telemetry_publish_mode') IS NULL IF COL_LENGTH('object_variables', 'telemetry_publish_mode') IS NULL ALTER TABLE object_variables ADD telemetry_publish_mode VARCHAR(32);
GO


-- ===== V78__variable_history_record_snapshot_text.sql =====
-- Full-variable historian snapshots ($record) may exceed 2 KiB.
ALTER TABLE variable_samples
    ALTER COLUMN value_text NVARCHAR(MAX);
GO


-- ===== V79__variable_policy_extensions.sql =====
IF COL_LENGTH('object_variables', 'history_sample_mode') IS NULL IF COL_LENGTH('object_variables', 'history_sample_mode') IS NULL ALTER TABLE object_variables ADD history_sample_mode VARCHAR(32) NOT NULL DEFAULT 'CHANGES_ONLY';
GO


IF COL_LENGTH('object_variables', 'include_previous_value_in_event') IS NULL IF COL_LENGTH('object_variables', 'include_previous_value_in_event') IS NULL ALTER TABLE object_variables ADD include_previous_value_in_event BIT NOT NULL DEFAULT 0;
GO


IF COL_LENGTH('object_variables', 'storage_mode') IS NULL IF COL_LENGTH('object_variables', 'storage_mode') IS NULL ALTER TABLE object_variables ADD storage_mode VARCHAR(32) NOT NULL DEFAULT 'PERSISTENT';
GO


-- ===== V80__retire_object_query_type.sql =====
-- Retire legacy ObjectType.QUERY catalog instances (no runtime backward compatibility).
UPDATE object_nodes
SET type = 'CUSTOM',
    template_id = NULL
WHERE type = 'QUERY';
GO


UPDATE object_nodes
SET type = 'CUSTOM',
    template_id = NULL
WHERE template_id = 'query-v1';
GO


-- ===== V81__ot_automation_excellence.sql =====
-- ADR-0049 Wave 1: step-level workflow execution journal
-- Wave 3: dead letters for exhausted retries

CREATE TABLE workflow_execution_steps (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    token_id        VARCHAR(64),
    seq             INTEGER      NOT NULL,
    node_id         VARCHAR(128) NOT NULL,
    node_type       VARCHAR(64)  NOT NULL,
    started_at      DATETIME2    NOT NULL,
    ended_at        DATETIME2,
    status          VARCHAR(16)  NOT NULL,
    attempt         INTEGER      NOT NULL DEFAULT 1,
    input_json      NVARCHAR(MAX),
    output_json     NVARCHAR(MAX),
    error_json      NVARCHAR(MAX),
    created_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_workflow_execution_steps_instance
    ON workflow_execution_steps(instance_id, seq);
GO

CREATE INDEX idx_workflow_execution_steps_path
    ON workflow_execution_steps(workflow_path, started_at DESC);
GO


CREATE TABLE workflow_dead_letters (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_error      NVARCHAR(MAX),
    payload_json    NVARCHAR(MAX),
    created_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     DATETIME2
);
GO


CREATE INDEX idx_workflow_dead_letters_path
    ON workflow_dead_letters(workflow_path, created_at DESC);
GO


-- ADR-0049 Wave 4: encrypted credentials vault
CREATE TABLE platform_credentials (
    id              VARCHAR(64)  PRIMARY KEY,
    object_path     VARCHAR(512) NOT NULL UNIQUE,
    kind            VARCHAR(64)  NOT NULL,
    cipher_text     NVARCHAR(MAX)         NOT NULL,
    metadata_json   NVARCHAR(MAX),
    created_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


CREATE INDEX idx_platform_credentials_kind
    ON platform_credentials(kind);
GO


-- ===== V82__alarm_shelf_requests.sql =====
-- BL-158: persisted alarm shelving approval requests

CREATE TABLE alarm_shelf_requests (
    id               VARCHAR(64)  PRIMARY KEY,
    object_path      VARCHAR(512) NOT NULL,
    event_name       VARCHAR(128) NOT NULL,
    alert_rule_path  VARCHAR(512),
    duration_minutes INTEGER,
    comment          VARCHAR(1024),
    requested_by     VARCHAR(128) NOT NULL,
    requested_at     DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_alarm_shelf_requests_requested_at
    ON alarm_shelf_requests (requested_at DESC);
GO


-- ===== V83__partner_program.sql =====
-- BL-184: persisted partner directory and enrollment applications

CREATE TABLE partner_directory (
    id                   VARCHAR(64)  PRIMARY KEY,
    name                 VARCHAR(256) NOT NULL,
    certification_level  VARCHAR(64)  NOT NULL,
    tier_id              VARCHAR(32)  NOT NULL,
    regions_json         NVARCHAR(MAX),
    verticals_json       NVARCHAR(MAX),
    marketplace_url      VARCHAR(512),
    certified_since      VARCHAR(16),
    status               VARCHAR(32)  NOT NULL DEFAULT 'certified'
);
GO


CREATE INDEX idx_partner_directory_tier
    ON partner_directory (tier_id);
GO


CREATE TABLE partner_enrollments (
    application_id  VARCHAR(64)  PRIMARY KEY,
    company_name    VARCHAR(256),
    contact_email   VARCHAR(256),
    tier_id         VARCHAR(32)  NOT NULL,
    verticals_json  NVARCHAR(MAX),
    regions_json    NVARCHAR(MAX),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACCEPTED',
    created_at      DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_partner_enrollments_created_at
    ON partner_enrollments (created_at DESC);
GO


-- ===== V84__workflow_retry_schedule.sql =====
-- ADR-0049 Wave 3: durable async workflow retry schedule

CREATE TABLE workflow_retry_schedule (
    id                  VARCHAR(64)  PRIMARY KEY,
    workflow_path       VARCHAR(512) NOT NULL,
    source_instance_id  VARCHAR(64)  NOT NULL,
    attempt             INTEGER      NOT NULL,
    due_at              DATETIME2    NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    input_json          NVARCHAR(MAX),
    last_error          NVARCHAR(MAX),
    created_at          DATETIME2    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at          DATETIME2,
    completed_at        DATETIME2
);
GO


CREATE INDEX idx_workflow_retry_due
    ON workflow_retry_schedule(status, due_at);
GO

CREATE INDEX idx_workflow_retry_path
    ON workflow_retry_schedule(workflow_path, created_at DESC);
GO


-- ===== V85__tenant_saas_roles.sql =====
-- BL-155: SaaS tenant-scoped custom roles (tenant-admin local owners)

IF COL_LENGTH('platform_roles', 'tenant_id') IS NULL IF COL_LENGTH('platform_roles', 'tenant_id') IS NULL ALTER TABLE platform_roles ADD tenant_id VARCHAR(64) NULL;
GO


CREATE INDEX idx_platform_roles_tenant ON platform_roles (tenant_id);
GO


-- skipped V86__tenant_row_level_security.sql (PG-only)

-- ===== V87__retire_event_frame_tree_catalog.sql =====
-- Retire decorative event-frame object-tree catalog (BL-208 runtime stays in platform_event_frames).
-- Frames are opened/closed via analytics REST + PG; no Explorer folder or instance-type blueprint.

DELETE FROM object_variables
WHERE object_path = 'root.platform.event-frames'
   OR object_path LIKE 'root.platform.event-frames.%'
   OR object_path = 'root.platform.instance-types.event-frame-v1';
GO


DELETE FROM object_acl_entries
WHERE object_path = 'root.platform.event-frames'
   OR object_path LIKE 'root.platform.event-frames.%'
   OR object_path = 'root.platform.instance-types.event-frame-v1';
GO


DELETE FROM object_nodes
WHERE path = 'root.platform.event-frames'
   OR path LIKE 'root.platform.event-frames.%'
   OR path = 'root.platform.instance-types.event-frame-v1'
   OR type IN ('EVENT_FRAMES', 'EVENT_FRAME')
   OR template_id = 'event-frame-v1';
GO


DELETE FROM blueprint_definitions
WHERE name = 'event-frame-v1';
GO


-- ===== V88__rename_mixin_singleton_blueprints.sql =====
-- Rename blueprint catalogs and types: RELATIVE→MIXIN, ABSOLUTE→SINGLETON.

-- Tree catalog paths (and children)
UPDATE object_nodes
SET path = REPLACE(path, 'relative-blueprints', 'mixin-blueprints')
WHERE path LIKE '%relative-blueprints%';
GO


UPDATE object_nodes
SET path = REPLACE(path, 'absolute-blueprints', 'singleton-blueprints')
WHERE path LIKE '%absolute-blueprints%';
GO


UPDATE object_nodes
SET display_name = 'Mixin Blueprints'
WHERE path = 'root.platform.mixin-blueprints';
GO


UPDATE object_nodes
SET display_name = 'Singleton Blueprints'
WHERE path = 'root.platform.singleton-blueprints';
GO


-- Dependent path columns
UPDATE object_variables
SET object_path = REPLACE(object_path, 'relative-blueprints', 'mixin-blueprints')
WHERE object_path LIKE '%relative-blueprints%';
GO


UPDATE object_variables
SET object_path = REPLACE(object_path, 'absolute-blueprints', 'singleton-blueprints')
WHERE object_path LIKE '%absolute-blueprints%';
GO


UPDATE object_acl_entries
SET object_path = REPLACE(object_path, 'relative-blueprints', 'mixin-blueprints')
WHERE object_path LIKE '%relative-blueprints%';
GO


UPDATE object_acl_entries
SET object_path = REPLACE(object_path, 'absolute-blueprints', 'singleton-blueprints')
WHERE object_path LIKE '%absolute-blueprints%';
GO


-- Blueprint definition JSON: type enum + singleton path parameter key
UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type":"RELATIVE"', '"type":"MIXIN"');
GO


UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type": "RELATIVE"', '"type": "MIXIN"');
GO


UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type":"ABSOLUTE"', '"type":"SINGLETON"');
GO


UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type": "ABSOLUTE"', '"type": "SINGLETON"');
GO


UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, 'absoluteInstancePath', 'singletonInstancePath');
GO


-- skipped V89__random_uuid_compat.sql (PG-only)

-- ===== V90__blueprint_contributions.sql =====
IF COL_LENGTH('object_nodes', 'blueprint_contributions') IS NULL IF COL_LENGTH('object_nodes', 'blueprint_contributions') IS NULL ALTER TABLE object_nodes ADD blueprint_contributions NVARCHAR(MAX);
GO


-- ===== V91__retire_mes_object_types.sql =====
-- MES catalogs are bundle-owned CUSTOM nodes under root.platform.mes.*.
-- Same retirement as V80 (QUERY -> CUSTOM). No runtime read of the old names.
UPDATE object_nodes
SET type = 'CUSTOM'
WHERE type IN (
    'MES',
    'WORK_ORDERS',
    'WORK_ORDER',
    'OPERATIONS',
    'OPERATION',
    'LOTS',
    'LOT',
    'SHIFTS',
    'SHIFT',
    'QUALITY_RECORDS',
    'QUALITY_RECORD',
    'MES_INSTANCES'
);
GO


-- skipped PG regexp_replace data rewrite (greenfield)

-- ===== V92__application_git_bindings.sql =====
-- ADR-0063: per-application Git Catalog binding (team remote + sync metadata).
CREATE TABLE application_git_bindings (
    app_id VARCHAR(64) PRIMARY KEY REFERENCES applications(app_id),
    provider VARCHAR(32) NOT NULL,
    remote_url VARCHAR(1024) NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    bundle_path VARCHAR(512) NOT NULL DEFAULT 'bundle.json',
    credential_object_path VARCHAR(512),
    last_synced_sha VARCHAR(64),
    last_synced_version VARCHAR(64),
    last_synced_at DATETIME2,
    updated_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
GO


-- ===== V93__application_bundle_deploy_git_provenance.sql =====
-- ADR-0063: deploy history provenance for Git Pull release.
IF COL_LENGTH('application_bundle_deployments', 'deploy_source') IS NULL IF COL_LENGTH('application_bundle_deployments', 'deploy_source') IS NULL ALTER TABLE application_bundle_deployments ADD deploy_source VARCHAR(32);
GO

IF COL_LENGTH('application_bundle_deployments', 'git_ref') IS NULL IF COL_LENGTH('application_bundle_deployments', 'git_ref') IS NULL ALTER TABLE application_bundle_deployments ADD git_ref VARCHAR(255);
GO

IF COL_LENGTH('application_bundle_deployments', 'git_sha') IS NULL IF COL_LENGTH('application_bundle_deployments', 'git_sha') IS NULL ALTER TABLE application_bundle_deployments ADD git_sha VARCHAR(64);
GO


-- ===== V94__variable_samples_quality.sql =====
-- ADR-0074: nullable historian quality. NULL means unknown / pre-v2, not GOOD.
IF COL_LENGTH('variable_samples', 'quality') IS NULL IF COL_LENGTH('variable_samples', 'quality') IS NULL ALTER TABLE variable_samples ADD quality VARCHAR(32);
GO


-- ===== V95__ui_pack_scoped_sessions.sql =====
-- ADR-0073: NULL ui_pack_app_id is a normal console session.
-- A non-null value is a BFF-scoped token for that hosted ui-pack.
IF COL_LENGTH('platform_auth_sessions', 'ui_pack_app_id') IS NULL IF COL_LENGTH('platform_auth_sessions', 'ui_pack_app_id') IS NULL ALTER TABLE platform_auth_sessions ADD ui_pack_app_id VARCHAR(64);
GO


-- ===== V96__historian_catchup_ranges.sql =====
-- Durable ClickHouse catch-up ranges (ADR-0035). Sample bytes live in the disk WAL;
-- this table is the range index that survives process restart.
-- DATETIME2, not DATETIMEOFFSET: H2 in PostgreSQL mode does not know DATETIMEOFFSET.
CREATE TABLE historian_catchup_ranges (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    series_key VARCHAR(1024) NOT NULL,
    from_ts DATETIME2 NOT NULL,
    to_ts DATETIME2 NOT NULL
);
GO


CREATE INDEX historian_catchup_ranges_series
    ON historian_catchup_ranges (series_key, from_ts);
GO


-- ===== V97__driver_lock_fencing_epoch.sql =====
IF COL_LENGTH('platform_driver_locks', 'fencing_epoch') IS NULL IF COL_LENGTH('platform_driver_locks', 'fencing_epoch') IS NULL ALTER TABLE platform_driver_locks ADD fencing_epoch BIGINT NOT NULL DEFAULT 0;
GO


UPDATE platform_driver_locks
SET fencing_epoch = 1
WHERE fencing_epoch = 0;
GO


-- skipped PG ALTER COLUMN fencing_epoch SET DEFAULT on platform_driver_locks (greenfield)

-- ===== V98__application_relay_audit_chain.sql =====
-- Application transactional outbox/inbox, audit hash chain, script advisory-lock backing table.

CREATE TABLE application_outbox (
    id              VARCHAR(64)  PRIMARY KEY,
    app_id          VARCHAR(128) NOT NULL,
    connector_id    VARCHAR(128) NOT NULL,
    message_id      VARCHAR(256) NOT NULL,
    body            NVARCHAR(MAX)         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attempts        INT          NOT NULL,
    created_at      DATETIME2    NOT NULL
);
GO


CREATE INDEX idx_application_outbox_pending ON application_outbox (status, created_at);
GO


CREATE TABLE application_inbox (
    connector_id          VARCHAR(128) NOT NULL,
    external_message_id   VARCHAR(256) NOT NULL,
    received_at           DATETIME2    NOT NULL,
    PRIMARY KEY (connector_id, external_message_id)
);
GO


CREATE TABLE audit_event_chain (
    seq            BIGINT IDENTITY(1,1),
    event_id       VARCHAR(64) PRIMARY KEY,
    previous_hash  VARCHAR(64),
    chain_hash     VARCHAR(64) NOT NULL,
    occurred_at    DATETIME2   NOT NULL
);
GO


CREATE INDEX idx_audit_event_chain_seq ON audit_event_chain (seq DESC);
GO


CREATE TABLE platform_script_locks (
    lock_key VARCHAR(200) PRIMARY KEY
);
GO


-- ===== V99__user_signature_keys.sql =====
-- Ed25519 public keys used to verify function signatures. Private keys stay with the signer.

CREATE TABLE user_signature_keys (
    username    VARCHAR(128) PRIMARY KEY,
    public_key  VARCHAR(256) NOT NULL,
    updated_at  DATETIME2    NOT NULL
);
GO


-- ===== V100__application_outbox_idempotency.sql =====
-- One platform outbox row per app, connector, and message id.
-- A repeated enqueue returns that row instead of posting a second delivery.

CREATE UNIQUE INDEX uq_application_outbox_idempotency
    ON application_outbox (app_id, connector_id, message_id);
GO


-- ===== V101__application_outbox_claim_lease.sql =====
-- Lease a pending outbox row so two pumps cannot post it.
-- V100 is already applied on the TCK database, so the lease column is a new migration.

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'uq_application_outbox_idempotency' AND object_id = OBJECT_ID(N'application_outbox')) DROP INDEX uq_application_outbox_idempotency ON application_outbox;
GO


DELETE FROM application_outbox
WHERE id IN (
    SELECT id FROM (
        SELECT a.id AS id
        FROM application_outbox a
        INNER JOIN application_outbox b
            ON a.app_id = b.app_id
           AND a.connector_id = b.connector_id
           AND a.message_id = b.message_id
           AND a.id <> b.id
        WHERE (b.status = 'SENT' AND a.status <> 'SENT')
           OR (
                CASE WHEN b.status = 'SENT' THEN 1 ELSE 0 END
                    = CASE WHEN a.status = 'SENT' THEN 1 ELSE 0 END
                AND (
                    a.created_at > b.created_at
                    OR (a.created_at = b.created_at AND a.id > b.id)
                )
           )
    ) doomed
);
GO


IF COL_LENGTH('application_outbox', 'claimed_until') IS NULL IF COL_LENGTH('application_outbox', 'claimed_until') IS NULL ALTER TABLE application_outbox ADD claimed_until DATETIME2 NULL;
GO


CREATE UNIQUE INDEX uq_application_outbox_idempotency
    ON application_outbox (app_id, connector_id, message_id);
GO

