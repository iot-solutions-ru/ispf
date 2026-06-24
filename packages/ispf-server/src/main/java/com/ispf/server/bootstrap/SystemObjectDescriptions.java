package com.ispf.server.bootstrap;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.security.PlatformUserService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical English display names and descriptions for platform system object nodes.
 * Shown in the object tree inspector and system folder list panels.
 */
public final class SystemObjectDescriptions {

    public record Entry(String displayName, String description) {}

    private static final String APPLICATIONS_PREFIX = "root.platform.applications.";
    private static final String OPERATOR_APPS_PREFIX = "root.platform.operator-apps.";
    private static final String FEDERATION_PREFIX = FederationPaths.FEDERATION_ROOT + ".";

    private static final Map<String, Entry> BY_EXACT_PATH = buildExactPaths();

    private SystemObjectDescriptions() {
    }

    private static Map<String, Entry> buildExactPaths() {
        Map<String, Entry> map = new LinkedHashMap<>();
        map.put("root", new Entry("Root", """
                Root of the ISPF object tree. All platform and tenant namespaces hang under this node. \
                Open root.platform for day-to-day administration or root.tenant for multi-tenant partitions."""));
        map.put("root.platform", new Entry("Platform", """
                Top-level platform namespace. Contains built-in catalogs (devices, dashboards, models, automation, \
                security, SQL objects) and user-created nodes. Select a child folder to manage that domain; \
                edit metadata in the inspector or open specialized editors with a double-click."""));
        map.put("root.tenant", new Entry("Tenants", """
                Multi-tenant namespace root. Each child tenant receives an isolated subtree \
                root.tenant.{tenantId}.platform.* — devices, dashboards, and applications scoped to that tenant. \
                Assign a tenantId to operator users in Security → Users to limit their visible tree."""));
        map.put("root.platform.devices", new Entry("Devices", """
                Device catalog. Each DEVICE child runs a driver plugin (SNMP, Modbus, MQTT, virtual lab, JDBC, etc.) \
                and exposes variables, events, and functions from its primary and applied models. \
                Start/stop drivers from the Driver tab; bind variables on the Bindings tab. \
                Use Federation bind (inspector) to proxy a remote device at a local path while keeping the operator path stable."""));
        map.put("root.platform.relative-models", new Entry("Relative Models", """
                Optional RELATIVE model blueprints (mixins). They enrich existing objects with extra variables, \
                events, functions, and binding rules without changing the object path. \
                Platform system types (data source, schedule, dashboard, …) embed their schema in each instance — \
                they are not listed here. Attach mixins to devices or folders via Applied models in the inspector."""));
        map.put("root.platform.instance-types", new Entry("Instance Types", """
                INSTANCE model blueprints — templates for new object instances (devices, folders, custom nodes). \
                When you create an object with a templateId, the platform materializes structure from the matching INSTANCE model. \
                Use Instance Types for repeatable device types; use Relative Models to add capabilities to existing nodes."""));
        map.put("root.platform.absolute-models", new Entry("Absolute Models", """
                ABSOLUTE model blueprints for singleton configuration objects. \
                Each absolute model has exactly one live instance under root.platform.instances. \
                Typical for platform-wide settings objects that must exist once."""));
        map.put("root.platform.instances", new Entry("Instances", """
                Live ABSOLUTE model instances. Each child is the single runtime object for its absolute model blueprint. \
                Edit variables and functions here; do not create duplicate instances for the same absolute model."""));
        map.put("root.platform.dashboards", new Entry("Dashboards", """
                HMI dashboard catalog. DASHBOARD objects store widget layouts for admin preview and operator mode. \
                Widgets bind to device variables, sub-dashboards, SQL reports, maps, and workflows. \
                Operator Apps reference dashboards by path; open the Dashboard editor with a double-click."""));
        map.put("root.platform.reports", new Entry("Reports", """
                Platform-wide report catalog. REPORT children use report-v1 (SQL against a data source) or \
                tree-variables-report-v1 (scan device variables by path pattern). \
                Configure columns, parameters, and optional YARG export templates (CSV, HTML, XLSX, PDF). \
                Application-owned reports may also live under root.platform.applications.{appId}.reports."""));
        map.put(DataSourcePathResolver.DATA_SOURCES_ROOT, new Entry("Data Sources", """
                PostgreSQL schema registry for SQL-backed platform features. \
                Each DATA_SOURCE child maps a display name to a schema name (e.g. app_myapp) used by reports, \
                SQL bindings, migrations, and script functions. \
                Create manually in the Data Source editor or import from an application bundle deploy."""));
        map.put(ScheduleObjectService.SCHEDULES_ROOT, new Entry("Schedules", """
                Platform scheduler catalog (tree-first). Each SCHEDULE child runs on a fixed interval (intervalMs) \
                and invokes a platform function (invoke_function action). Jobs are persisted in the object tree and survive restarts. \
                Application bundles may register schedules under root.platform.applications.{appId}.schedules."""));
        map.put(SqlBindingObjectService.BINDINGS_ROOT, new Entry("SQL Bindings", """
                SQL-to-variable binding catalog. Each BINDING child runs a SQL statement against a registered data source \
                on a poll interval and writes the result into target object variables. \
                Used for bringing relational data into the live object model without custom drivers."""));
        map.put(MigrationObjectService.MIGRATIONS_ROOT, new Entry("Migrations", """
                SQL migration script catalog. Each MIGRATION child stores DDL/DML applied to a data source schema, \
                typically during bundle import. The Migration editor tracks checksum, apply state, and history. \
                Platform migrations live here; app-specific scripts may also appear under applications.{appId}.migrations."""));
        map.put("root.platform.workflows", new Entry("Workflows", """
                BPMN 2.0 workflow catalog. WORKFLOW children define automation processes integrated with NATS messaging, \
                device functions, timers, and user tasks. Activate workflows from the Workflow editor; \
                link an Operator App so user tasks appear in the operator sidebar Work queue. \
                Correlators can start workflows when event patterns match."""));
        map.put("root.platform.alert-rules", new Entry("Alert Rules", """
                CEL alert rule catalog. Each ALERT child watches variable changes on a target object and publishes \
                an event when the CEL condition is true (optionally edge-triggered to fire only on false→true transitions). \
                Use alert rules to turn telemetry into discrete alarm events consumed by correlators, workflows, or dashboards."""));
        map.put("root.platform.correlators", new Entry("Correlators", """
                Event correlator catalog. Each CORRELATOR child matches event patterns across objects — COUNT (event A then B within a window) \
                or SEQUENCE (ordered chain) — and triggers a workflow or publishes a follow-up event. \
                Place correlators between alert rules and BPMN automation."""));
        map.put("root.platform.applications", new Entry("Applications", """
                Deployed application bundle containers. Each APPLICATION child (folder name = appId / packageId) holds standard subfolders: \
                functions, reports, schedules, bindings, migrations, and optional screens. \
                Import packages via Explorer deploy, AI Studio bundle publish, or REST deploy API. \
                Bundle content is expanded into both root.platform.applications.{appId}.* and shared platform catalogs where applicable."""));
        map.put("root.platform.operator-apps", new Entry("Operator Apps", """
                Operator HMI application registry. Each OPERATOR_APP child defines the operator shell: title, dashboard and report navigation, \
                optional alarm bar rules, and work queue scope for URL ?mode=operator&app={appId}. \
                Unlike APPLICATION bundles, operator apps reference existing DASHBOARD/REPORT paths in the tree — they do not embed layout JSON. \
                Configure from Explorer → Operator Apps or create via + Operator app."""));
        map.put(FederationPaths.FEDERATION_ROOT, new Entry("Federation", """
                Federation hub for remote ISPF instances. Object path and service endpoint are separate concerns: \
                register each peer's base URL in System → Federation → Peers (this tree folder holds catalog mirrors, not peer credentials). \
                For loopback or tunnel testing, add a peer pointing at the tunnel endpoint and leave auth empty when using registration codes; \
                for production remote sites, set authToken to a service account Bearer or use Get token / issue token on the Tokens tab. \
                Catalog sync imports the remote object list under root.platform.federation.{peerName}.* as read-only mirrors. \
                Federation bind (object inspector) overlays a remote object onto any local path you choose for production HMI and drivers. \
                Mirror nodes here are for discovery — use Place locally or bind to adopt remote data at operator-facing paths."""));
        map.put(PlatformUserService.SECURITY_ROOT, new Entry("Security", """
                Platform authentication and RBAC root. Contains Users and Roles folders synchronized with the security store. \
                Manage accounts via System → Security panels or REST /api/v1/security/*. \
                OIDC mode delegates login to Keycloak; local users remain in the tree for role assignment and auto-start operator settings."""));
        map.put(PlatformUserService.USERS_FOLDER, new Entry("Users", """
                Platform user account folder. Each USER child mirrors one login: username, displayName, role list, enabled flag, \
                and operator auto-start (open a specific Operator App after login). \
                Admin users see the full admin console; operator users land in operator mode. \
                CRUD via Security → Users or POST/PUT/DELETE /api/v1/security/users (admin role). \
                Deleting a USER node removes the backing account."""));
        map.put(PlatformUserService.ROLES_FOLDER, new Entry("Roles", """
                RBAC role definitions. Each ROLE child stores roleName and description; users reference roles by name in their roles variable. \
                Built-in admin and operator roles are seeded at first startup. \
                Custom roles extend authorization checks across REST, WebSocket, and UI features."""));
        return Map.copyOf(map);
    }

    private static final Entry APPLICATION_INSTANCE = new Entry(null, """
            Application bundle root for this appId. Deploy imports populate child folders: \
            functions (callable API handlers), reports, schedules, bindings, migrations, and optional legacy screens. \
            The appId equals the package folder name and is used in deploy REST paths and SQL schema defaults.""");

    private static final Entry APPLICATION_FUNCTIONS = new Entry("Functions", """
            Callable functions owned by this application bundle. Each FUNCTION child is invoked via \
            POST /api/v1/objects/by-path/.../functions/{name}/invoke, workflows, schedules, or dashboard widgets. \
            Implementations may be script, nested platform function, or JDBC against the app's data source.""");

    private static final Entry APPLICATION_REPORTS = new Entry("Reports", """
            SQL and tree reports shipped with this application. Same report-v1 semantics as root.platform.reports but scoped to the app deploy. \
            Data sources typically resolve to root.platform.data-sources.{appId}.""");

    private static final Entry APPLICATION_SCHEDULES = new Entry("Schedules", """
            Cron-style interval jobs registered by this application bundle. Each SCHEDULE invokes an app function on intervalMs.""");

    private static final Entry APPLICATION_BINDINGS = new Entry("SQL Bindings", """
            SQL-to-variable bindings owned by this application. Poll SQL against the app schema and write into object variables.""");

    private static final Entry APPLICATION_MIGRATIONS = new Entry("Migrations", """
            Database migrations shipped with this application. Applied automatically on bundle import or manually from the Migration editor.""");

    private static final Entry APPLICATION_SCREENS = new Entry("Screens", """
            Legacy operator manifest screens from older bundle formats. Prefer Operator Apps + dashboards for new HMI work.""");

    private static final Entry OPERATOR_APP_INSTANCE = new Entry(null, """
            Operator HMI entry for this appId. Variables define title, ordered dashboard/report lists, alarm bar configuration, \
            and workflow user-task scope. Open operator mode with ?mode=operator&app={appId}. \
            Does not contain widget JSON — it points at DASHBOARD and REPORT paths elsewhere in the tree.""");

    public static Optional<Entry> resolve(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        Entry exact = BY_EXACT_PATH.get(path);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (path.startsWith(APPLICATIONS_PREFIX)) {
            return resolveApplicationPath(path.substring(APPLICATIONS_PREFIX.length()));
        }
        if (path.startsWith(OPERATOR_APPS_PREFIX)) {
            String rest = path.substring(OPERATOR_APPS_PREFIX.length());
            if (!rest.isBlank() && !rest.contains(".")) {
                return Optional.of(OPERATOR_APP_INSTANCE);
            }
        }
        if (path.startsWith(FEDERATION_PREFIX)) {
            String rest = path.substring(FEDERATION_PREFIX.length());
            if (!rest.isBlank() && !rest.contains(".")) {
                String peerName = rest;
                return Optional.of(new Entry(
                        peerName,
                        """
                                Catalog mirror for federation peer "%s". \
                                Child nodes are read-only proxies of objects on the remote ISPF site (devices, dashboards, workflows, etc.). \
                                Use Federation → Sync catalog to refresh. \
                                To use remote data in production HMI, either Place locally… from a mirror node or Federation bind onto an existing local path in the inspector.""".formatted(peerName)
                ));
            }
        }
        return Optional.empty();
    }

    private static Optional<Entry> resolveApplicationPath(String rest) {
        if (rest.isBlank()) {
            return Optional.empty();
        }
        if (!rest.contains(".")) {
            return Optional.of(APPLICATION_INSTANCE);
        }
        if (rest.endsWith(".functions")) {
            return Optional.of(APPLICATION_FUNCTIONS);
        }
        if (rest.endsWith(".reports")) {
            return Optional.of(APPLICATION_REPORTS);
        }
        if (rest.endsWith(".schedules")) {
            return Optional.of(APPLICATION_SCHEDULES);
        }
        if (rest.endsWith(".bindings")) {
            return Optional.of(APPLICATION_BINDINGS);
        }
        if (rest.endsWith(".migrations")) {
            return Optional.of(APPLICATION_MIGRATIONS);
        }
        if (rest.endsWith(".screens")) {
            return Optional.of(APPLICATION_SCREENS);
        }
        return Optional.empty();
    }

    public static Map<String, Entry> exactPaths() {
        return BY_EXACT_PATH;
    }
}
