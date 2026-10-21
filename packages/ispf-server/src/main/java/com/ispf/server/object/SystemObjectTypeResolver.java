package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.server.process.ProcessProgramPaths;

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
            Map.entry("root.platform.queries", ObjectType.QUERIES),
            Map.entry("root.platform.event-filters", ObjectType.EVENT_FILTERS),
            Map.entry(ProcessProgramPaths.PROCESS_PROGRAMS_ROOT, ObjectType.PROCESS_PROGRAMS),
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
            Map.entry("migration-v1", ObjectType.MIGRATION),
            Map.entry("analytics-template-v1", ObjectType.ANALYTICS_TEMPLATE),
            Map.entry("event-filter-v1", ObjectType.EVENT_FILTER),
            Map.entry("process-program-v1", ObjectType.PROCESS_PROGRAM)
    );

    private SystemObjectTypeResolver() {
    }

    /** True for bootstrap catalog roots (DEVICES, QUERIES, …) — not for mis-typed children. */
    public static boolean isExactCatalogPath(String path) {
        return path != null && BY_EXACT_PATH.containsKey(path);
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

    /**
     * Catalog folder types are positional. A blueprint named {@code migrations} must not become
     * {@link ObjectType#MIGRATIONS} just because its path ends with that leaf.
     */
    private static ObjectType resolveFolderSuffix(String path) {
        if (path == null) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        String leaf = path.substring(dot + 1);
        ObjectType type = switch (leaf) {
            case "reports" -> ObjectType.REPORTS;
            case "data-sources" -> ObjectType.DATA_SOURCES;
            case "functions" -> ObjectType.FUNCTIONS;
            case "schedules" -> ObjectType.SCHEDULES;
            case "bindings" -> ObjectType.BINDINGS;
            case "migrations" -> ObjectType.MIGRATIONS;
            case "screens" -> ObjectType.SCREENS;
            case "process-programs" -> ObjectType.PROCESS_PROGRAMS;
            default -> null;
        };
        if (type == null || !isCatalogFolderPath(path, leaf)) {
            return null;
        }
        return type;
    }

    /**
     * {@code root.platform.{leaf}}, {@code root.tenant.{id}.platform.{leaf}},
     * or the same leaf directly under an application ({@code applications.{appId}.{leaf}}).
     */
    static boolean isCatalogFolderPath(String path, String leaf) {
        if (path == null || leaf == null || leaf.isBlank()) {
            return false;
        }
        if (path.equals("root.platform." + leaf)) {
            return true;
        }
        String[] parts = path.split("\\.", -1);
        if (parts.length == 5
                && "root".equals(parts[0])
                && "tenant".equals(parts[1])
                && "platform".equals(parts[3])
                && leaf.equals(parts[4])
                && !parts[2].isEmpty()) {
            return true;
        }
        if (parts.length == 5
                && "root".equals(parts[0])
                && "platform".equals(parts[1])
                && "applications".equals(parts[2])
                && leaf.equals(parts[4])
                && !parts[3].isEmpty()) {
            return true;
        }
        return parts.length == 7
                && "root".equals(parts[0])
                && "tenant".equals(parts[1])
                && "platform".equals(parts[3])
                && "applications".equals(parts[4])
                && leaf.equals(parts[6])
                && !parts[2].isEmpty()
                && !parts[5].isEmpty();
    }
}
