package com.ispf.server.object;

import com.ispf.core.object.ObjectType;

import java.util.Map;
import java.util.Optional;

public final class SystemObjectTypeResolver {

    private static final Map<String, ObjectType> BY_EXACT_PATH = Map.ofEntries(
            Map.entry("root.platform", ObjectType.PLATFORM),
            Map.entry("root.platform.devices", ObjectType.DEVICES),
            Map.entry("root.platform.dashboards", ObjectType.DASHBOARDS),
            Map.entry("root.platform.reports", ObjectType.REPORTS),
            Map.entry("root.platform.data-sources", ObjectType.DATA_SOURCES),
            Map.entry("root.platform.schedules", ObjectType.SCHEDULES),
            Map.entry("root.platform.bindings", ObjectType.BINDINGS),
            Map.entry("root.platform.migrations", ObjectType.MIGRATIONS),
            Map.entry("root.platform.workflows", ObjectType.WORKFLOWS),
            Map.entry("root.platform.alert-rules", ObjectType.ALERT_RULES),
            Map.entry("root.platform.correlators", ObjectType.CORRELATORS),
            Map.entry("root.platform.applications", ObjectType.APPLICATIONS),
            Map.entry("root.platform.operator-apps", ObjectType.OPERATOR_APPS),
            Map.entry("root.platform.security", ObjectType.SECURITY),
            Map.entry("root.platform.security.users", ObjectType.USERS),
            Map.entry("root.platform.security.roles", ObjectType.ROLES)
    );

    private static final Map<String, ObjectType> BY_TEMPLATE = Map.ofEntries(
            Map.entry("platform-role-v1", ObjectType.ROLE),
            Map.entry("application-function-v1", ObjectType.FUNCTION),
            Map.entry("application-schedule-v1", ObjectType.SCHEDULE),
            Map.entry("application-binding-v1", ObjectType.BINDING),
            Map.entry("application-migration-v1", ObjectType.MIGRATION),
            Map.entry("operator-screen-v1", ObjectType.SCREEN),
            Map.entry("report-v1", ObjectType.REPORT),
            Map.entry("tree-variables-report-v1", ObjectType.REPORT),
            Map.entry("data-source-v1", ObjectType.DATA_SOURCE),
            Map.entry("schedule-v1", ObjectType.SCHEDULE),
            Map.entry("sql-binding-v1", ObjectType.BINDING),
            Map.entry("migration-v1", ObjectType.MIGRATION)
    );

    private SystemObjectTypeResolver() {
    }

    public static Optional<ObjectType> resolve(String path, String templateId) {
        ObjectType exact = BY_EXACT_PATH.get(path);
        if (exact != null) {
            return Optional.of(exact);
        }
        ObjectType folder = resolveFolderSuffix(path);
        if (folder != null) {
            return Optional.of(folder);
        }
        if (templateId != null && !templateId.isBlank()) {
            ObjectType byTemplate = BY_TEMPLATE.get(templateId);
            if (byTemplate != null) {
                return Optional.of(byTemplate);
            }
        }
        return Optional.empty();
    }

    private static ObjectType resolveFolderSuffix(String path) {
        if (path.endsWith(".reports")) {
            return ObjectType.REPORTS;
        }
        if (path.endsWith(".data-sources")) {
            return ObjectType.DATA_SOURCES;
        }
        if (path.endsWith(".functions")) {
            return ObjectType.FUNCTIONS;
        }
        if (path.endsWith(".schedules")) {
            return ObjectType.SCHEDULES;
        }
        if (path.endsWith(".bindings")) {
            return ObjectType.BINDINGS;
        }
        if (path.endsWith(".migrations")) {
            return ObjectType.MIGRATIONS;
        }
        if (path.endsWith(".screens")) {
            return ObjectType.SCREENS;
        }
        return null;
    }
}
