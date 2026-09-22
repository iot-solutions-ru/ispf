package com.ispf.core.object;

/**
 * Classification of object nodes in the resource tree.
 */
public enum ObjectType {
    ROOT,
    TENANT,
    USER,
    /** Platform root node ({@code root.platform}). */
    PLATFORM,
    /** Device catalog folder. */
    DEVICES,
    DEVICE,
    DRIVER,
    BLUEPRINT,
    /** Dashboard catalog folder. */
    DASHBOARDS,
    DASHBOARD,
    /** SCADA mimic catalog folder. */
    MIMICS,
    MIMIC,
    /** Workflow catalog folder. */
    WORKFLOWS,
    WORKFLOW,
    /** Alert rule catalog folder. */
    ALERT_RULES,
    ALERT,
    /** Correlator catalog folder. */
    CORRELATORS,
    CORRELATOR,
    /** Application catalog folder. */
    APPLICATIONS,
    APPLICATION,
    /** Operator HMI apps folder. */
    OPERATOR_APPS,
    /** Security & RBAC root folder. */
    SECURITY,
    /** User accounts folder. */
    USERS,
    /** Roles folder. */
    ROLES,
    /** Platform role definition. */
    ROLE,
    /** SQL data source catalog folder. */
    DATA_SOURCES,
    /** JDBC schema reference for reports, bindings, script SQL. */
    DATA_SOURCE,
    /** Application reports folder. */
    REPORTS,
    REPORT,
    /** Object-query catalog folder ({@link com.ispf.server.query.ObjectQueryCatalog#QUERIES_ROOT}). */
    QUERIES,
    /** Reusable event log filter catalog folder (Phase 30). */
    EVENT_FILTERS,
    EVENT_FILTER,
    /** Application functions folder. */
    FUNCTIONS,
    FUNCTION,
    /** Application schedules folder. */
    SCHEDULES,
    SCHEDULE,
    /** Application SQL bindings folder. */
    BINDINGS,
    BINDING,
    /** Application migrations folder. */
    MIGRATIONS,
    MIGRATION,
    /** Operator screens folder. */
    SCREENS,
    SCREEN,
    AGENT,
    /** Visual-only grouping node; members stored in {@code @groupMembers}. */
    VISUAL_GROUP,
    /** Cyclic process-control program catalog folder (Phase 30). */
    PROCESS_PROGRAMS,
    /** Cyclic control loop program instance. */
    PROCESS_PROGRAM,
    /** Asset analytics template catalog folder (Phase 28). */
    ANALYTICS,
    /** Derived tag / KPI template instance. */
    ANALYTICS_TEMPLATE,
    /**
     * Extension object. MES catalogs (work orders, operations, lots, shifts, quality records)
     * are CUSTOM nodes under {@code root.platform.mes.*}, owned by the mes-platform bundle.
     */
    CUSTOM
}
