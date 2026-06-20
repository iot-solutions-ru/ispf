UPDATE object_nodes SET type = 'PLATFORM' WHERE path = 'root.platform';
UPDATE object_nodes SET type = 'DEVICES' WHERE path = 'root.platform.devices';
UPDATE object_nodes SET type = 'DASHBOARDS' WHERE path = 'root.platform.dashboards';
UPDATE object_nodes SET type = 'WORKFLOWS' WHERE path = 'root.platform.workflows';
UPDATE object_nodes SET type = 'ALERT_RULES' WHERE path = 'root.platform.alert-rules';
UPDATE object_nodes SET type = 'CORRELATORS' WHERE path = 'root.platform.correlators';
UPDATE object_nodes SET type = 'APPLICATIONS' WHERE path = 'root.platform.applications';
UPDATE object_nodes SET type = 'OPERATOR_APPS' WHERE path = 'root.platform.operator-apps';
UPDATE object_nodes SET type = 'SECURITY' WHERE path = 'root.platform.security';
UPDATE object_nodes SET type = 'USERS' WHERE path = 'root.platform.security.users';
UPDATE object_nodes SET type = 'ROLES' WHERE path = 'root.platform.security.roles';

UPDATE object_nodes SET type = 'ROLE' WHERE template_id = 'platform-role-v1';
UPDATE object_nodes SET type = 'FUNCTION' WHERE template_id = 'application-function-v1';
UPDATE object_nodes SET type = 'SCHEDULE' WHERE template_id = 'application-schedule-v1';
UPDATE object_nodes SET type = 'BINDING' WHERE template_id = 'application-binding-v1';
UPDATE object_nodes SET type = 'MIGRATION' WHERE template_id = 'application-migration-v1';
UPDATE object_nodes SET type = 'SCREEN' WHERE template_id = 'operator-screen-v1';

UPDATE object_nodes SET type = 'REPORTS' WHERE path LIKE '%.reports' AND type = 'CUSTOM';
UPDATE object_nodes SET type = 'FUNCTIONS' WHERE path LIKE '%.functions' AND type = 'CUSTOM';
UPDATE object_nodes SET type = 'SCHEDULES' WHERE path LIKE '%.schedules' AND type = 'CUSTOM';
UPDATE object_nodes SET type = 'BINDINGS' WHERE path LIKE '%.bindings' AND type = 'CUSTOM';
UPDATE object_nodes SET type = 'MIGRATIONS' WHERE path LIKE '%.migrations' AND type = 'CUSTOM';
UPDATE object_nodes SET type = 'SCREENS' WHERE path LIKE '%.screens' AND type = 'CUSTOM';
