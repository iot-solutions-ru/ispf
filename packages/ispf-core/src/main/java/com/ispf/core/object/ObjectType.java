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
    /** Cross-object query catalog folder (Phase 30). */
    QUERIES,
    QUERY,
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
    /** MES catalog root ({@code root.platform.mes}). */
    MES,
    /** Work order catalog folder. */
    WORK_ORDERS,
    /** Manufacturing work order instance. */
    WORK_ORDER,
    /** Operation catalog folder. */
    OPERATIONS,
    /** Manufacturing operation instance. */
    OPERATION,
    /** Material lot catalog folder. */
    LOTS,
    /** Material lot / batch instance. */
    LOT,
    /** Production shift catalog folder. */
    SHIFTS,
    /** Production shift instance. */
    SHIFT,
    /** Quality record catalog folder. */
    QUALITY_RECORDS,
    /** Quality inspection / defect record. */
    QUALITY_RECORD,
    /** ISA-95 site/area/line instance hierarchy folder. */
    MES_INSTANCES,
    /** Cyclic process-control program catalog folder (Phase 30). */
    PROCESS_PROGRAMS,
    /** Cyclic control loop program instance. */
    PROCESS_PROGRAM,
    /** Asset analytics template catalog folder (Phase 28). */
    ANALYTICS,
    /** Derived tag / KPI template instance. */
    ANALYTICS_TEMPLATE,
    CUSTOM
}
