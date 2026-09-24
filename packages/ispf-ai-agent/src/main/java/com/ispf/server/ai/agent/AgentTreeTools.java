package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import com.ispf.server.application.bundle.BundleValidationResult;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.dashboard.DashboardWidgetNormalizer;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.workflow.WorkflowService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tree, dashboard, driver, and bundle tools that stayed inline on the registry.
 */
final class AgentTreeTools {

    private AgentTreeTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            VariableMemberAccessService variableMemberAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService,
            ObjectMapper objectMapper,
            DriverRuntimeService driverRuntimeService,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DeviceProvisioningService deviceProvisioningService,
            FederationBindService federationBindService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AiToolRegistry aiToolRegistry,
            ApplicationBundleDeployService bundleDeployService
    ) {
        return List.of(
                listObjectsTool(ObjectTreePort, objectAccessService, tenantScopeService, objectUiIconService),
                getObjectTool(ObjectTreePort, objectAccessService, tenantScopeService, objectUiIconService),
                listVariablesTool(
                        ObjectTreePort,
                        objectAccessService,
                        variableMemberAccessService,
                        tenantScopeService
                ),
                setVariableTool(ObjectTreePort, variableMemberAccessService, objectMapper),
                configureDriverTool(ObjectTreePort, objectAccessService, driverRuntimeService, objectMapper),
                driverControlTool(objectAccessService, driverRuntimeService),
                createObjectTool(
                        ObjectTreePort,
                        objectAccessService,
                        objectTemplateService,
                        dashboardService,
                        reportService,
                        workflowService,
                        automationTreeService,
                        deviceProvisioningService,
                        federationBindService,
                        objectUiIconService
                ),
                deleteObjectTool(
                        ObjectTreePort,
                        objectAccessService,
                        tenantScopeService,
                        driverRuntimeService,
                        platformUserService,
                        platformRoleService,
                        automationTreeService
                ),
                getDashboardLayoutTool(dashboardService, objectAccessService, tenantScopeService, objectMapper),
                setDashboardLayoutTool(dashboardService, objectAccessService, tenantScopeService, objectMapper),
                addDashboardWidgetTool(dashboardService, objectAccessService, tenantScopeService, objectMapper),
                validateBundleTool(objectMapper, aiToolRegistry),
                dryRunDeployTool(objectMapper, aiToolRegistry),
                importPackageTool(objectMapper, bundleDeployService)
        );
    }

    private static PlatformAgentTool listObjectsTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_objects";
            }

            @Override
            public String description() {
                return "List child objects under a parent path. Args: parent or parentPath (default root), lite (bool, default true).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parent = stringArg(arguments, "parent");
                if (parent.isBlank()) {
                    parent = stringArg(arguments, "parentPath");
                }
                if (parent.isBlank()) {
                    parent = "root";
                }
                boolean lite = boolArg(arguments, "lite", true);
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(parent, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parent);
                }
                objectAccessService.requireRead(parent, auth);
                Function<PlatformObject, ObjectDto> mapper = lite
                        ? node -> ObjectDto.fromLite(node, objectUiIconService.readIconId(node).orElse(null))
                        : node -> ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
                List<ObjectDto> children = ObjectTreePort.tree().childrenOf(parent).stream()
                        .filter(node -> tenantScopeService.isPathVisible(node.path(), auth))
                        .filter(node -> objectAccessService.canRead(node.path(), auth))
                        .map(mapper)
                        .toList();
                return Map.of("status", "OK", "parent", parent, "count", children.size(), "objects", children);
            }
        };
    }

    private static PlatformAgentTool getObjectTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_object";
            }

            @Override
            public String description() {
                return "Get one object by path. Args: path (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                PlatformObject node = ObjectTreePort.require(path);
                ObjectDto dto = ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
                return Map.of("status", "OK", "object", dto);
            }
        };
    }

    private static PlatformAgentTool createObjectTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DeviceProvisioningService deviceProvisioningService,
            FederationBindService federationBindService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_object";
            }

            @Override
            public String description() {
                return "Create object tree node. Args: parentPath, name, type (DEVICE|DASHBOARD|CUSTOM|...), "
                        + "displayName, description?, templateId?, driverId?, driverPollIntervalMs?, autoStartDriver?.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parentPath = stringArg(arguments, "parentPath");
                String name = stringArg(arguments, "name");
                String typeRaw = stringArg(arguments, "type");
                String displayName = stringArg(arguments, "displayName");
                if (parentPath.isBlank() || name.isBlank() || typeRaw.isBlank() || displayName.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "parentPath, name, type, displayName are required"
                    );
                }
                ObjectType type = ObjectType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
                var auth = context.authentication();
                if (ObjectTreePort.tree().findByPath(parentPath).isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Parent not found: " + parentPath,
                            "hint", "Call list_objects on an existing parent folder and use paths from the tool result — "
                                    + "do not invent parentPath from playbooks or recipes."
                    );
                }
                objectAccessService.requireWrite(parentPath, auth);
                federationBindService.assertParentAllowsChildren(parentPath);
                String fullPath = ObjectTreePort.tree().resolveChildPath(parentPath, name);
                if (ObjectTreePort.tree().findByPath(fullPath).isPresent()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Object exists: " + fullPath,
                            "hint", "Reuse this object: get_object path=" + fullPath
                                    + " and list_variables — do not create a duplicate.",
                            "existingPath", fullPath
                    );
                }
                String templateId = optionalString(arguments, "templateId");
                String description = optionalString(arguments, "description");
                PlatformObject node = ObjectTreePort.create(
                        parentPath,
                        name,
                        type,
                        displayName,
                        description,
                        templateId
                );
                objectTemplateService.applyTemplate(node.path(), templateId);
                if (type == ObjectType.DASHBOARD) {
                    dashboardService.ensureDashboardStructure(node.path());
                }
                if (type == ObjectType.REPORT) {
                    reportService.ensureReportStructure(node.path());
                }
                if (type == ObjectType.WORKFLOW) {
                    workflowService.ensureWorkflowStructure(node.path());
                }
                if (type == ObjectType.ALERT) {
                    automationTreeService.ensureAlertRuleStructure(node.path());
                }
                if (type == ObjectType.CORRELATOR) {
                    automationTreeService.ensureCorrelatorStructure(node.path());
                }
                String driverId = optionalString(arguments, "driverId");
                if (type == ObjectType.DEVICE && driverId != null && !driverId.isBlank()) {
                    deviceProvisioningService.provisionDriver(
                            node.path(),
                            driverId,
                            intArg(arguments, "driverPollIntervalMs", 5000),
                            boolArg(arguments, "autoStartDriver", true)
                    );
                    ObjectTreePort.persistNodeTree(node.path());
                }
                PlatformObject saved = ObjectTreePort.require(node.path());
                ObjectDto created = ObjectDto.from(saved, objectUiIconService.readIconId(saved).orElse(null));
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "OK");
                response.put("path", node.path());
                response.put("object", created);
                if (type == ObjectType.DEVICE && "virtual".equals(driverId)) {
                    String tpl = templateId != null ? templateId : "";
                    if (tpl.isBlank() || tpl.contains("device-v1")) {
                        response.put(
                                "warning",
                                "Virtual DEVICE needs templateId virtual-lab-v1 or virtual-unified-v1, "
                                        + "or use create_virtual_device. Without model template there are no telemetry variables."
                        );
                    }
                }
                return response;
            }
        };
    }

    private static PlatformAgentTool deleteObjectTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            DriverRuntimeService driverRuntimeService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AutomationTreeService automationTreeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "delete_object";
            }

            @Override
            public String description() {
                return "Delete object tree node by path (and descendants). Args: path (required). "
                        + "Stops device driver if running. Cannot delete root.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                if ("root".equals(path)) {
                    return Map.of("status", "ERROR", "error", "Cannot delete root object");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    if (ObjectTreePort.tree().findByPath(path).isEmpty()) {
                        return Map.of("status", "ERROR", "error", "Object not found: " + path);
                    }
                    if (platformUserService.isSecurityUserPath(path)) {
                        platformUserService.deleteUser(platformUserService.usernameFromPath(path));
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    if (platformRoleService.isSecurityRolePath(path)) {
                        platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    if (ObjectTreePort.tree().findByPath(path)
                            .filter(node -> node.type() == ObjectType.CORRELATOR)
                            .isPresent()) {
                        automationTreeService.deleteCorrelator(path);
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    ObjectTreePort.tree().findByPath(path)
                            .filter(node -> node.type() == ObjectType.DEVICE)
                            .ifPresent(node -> driverRuntimeService.stopIfRunning(path));
                    ObjectTreePort.delete(path);
                    return Map.of("status", "OK", "path", path, "deleted", true);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool getDashboardLayoutTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_dashboard_layout";
            }

            @Override
            public String description() {
                return "Read dashboard layout JSON. Args: path (dashboard object path) OR template "
                        + "(snmp-host-monitoring | virtual-cluster-overview | virtual-cluster-detail | empty). "
                        + "Call before editing. Returns layoutJson for set_dashboard_layout. "
                        + "Prefer template= over hand-building full layout.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String template = optionalString(arguments, "template");
                try {
                    String layoutJson;
                    String source;
                    if (template != null) {
                        layoutJson = dashboardService.resolveTemplateLayout(template);
                        source = "template:" + template;
                    } else if (!path.isBlank()) {
                        var auth = context.authentication();
                        if (!tenantScopeService.isPathVisible(path, auth)) {
                            return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                        }
                        objectAccessService.requireRead(path, auth);
                        DashboardService.DashboardView view = dashboardService.getDashboard(path);
                        layoutJson = view.layoutJson();
                        source = "path:" + path;
                    } else {
                        return Map.of("status", "ERROR", "error", "path or template is required");
                    }
                    return Map.of(
                            "status", "OK",
                            "source", source,
                            "layoutJson", layoutJson,
                            "widgetCount", widgetCount(objectMapper, layoutJson),
                            "templates", DashboardService.layoutTemplateNames(),
                            "hint", "Widgets live in layout JSON only. Presentable grid: columns=84 rowHeight=8; "
                                    + "KPI w=21|28 h=14; charts/tables ≥42×28. "
                                    + "get_widget_catalog for types; set_dashboard_layout or add_dashboard_widget to edit."
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool setDashboardLayoutTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "set_dashboard_layout";
            }

            @Override
            public String description() {
                return "Replace dashboard layout variable. Args: path (required), layoutJson (full JSON string) "
                        + "OR template (snmp-host-monitoring | virtual-cluster-overview | "
                        + "virtual-cluster-detail | empty). Prefer template= for standard screens. "
                        + "Custom layoutJson MUST be columns=84,rowHeight=8; KPI tiles w=21|28 h=14 in a filled row; "
                        + "charts/tables w≥42 h≥28; sizes multiples of 7 — never legacy crumbs w=2..6. "
                        + "Requires DASHBOARD object at path. Never use set_variable for layout.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    String template = optionalString(arguments, "template");
                    DashboardService.DashboardView view;
                    if (template != null) {
                        view = dashboardService.applyTemplateLayout(path, template);
                    } else {
                        String layoutJson = optionalString(arguments, "layoutJson");
                        if (layoutJson == null) {
                            layoutJson = optionalString(arguments, "layout");
                        }
                        if (layoutJson == null) {
                            Object layoutObject = arguments.get("layout");
                            if (layoutObject == null) {
                                layoutObject = arguments.get("layoutJson");
                            }
                            if (layoutObject != null && !(layoutObject instanceof String)) {
                                layoutJson = DashboardWidgetNormalizer.resolveLayoutJson(layoutObject, objectMapper);
                            }
                        }
                        if (layoutJson == null || layoutJson.isBlank()) {
                            return Map.of(
                                    "status", "ERROR",
                                    "error",
                                    "layoutJson or template is required"
                            );
                        }
                        view = dashboardService.saveLayout(path, layoutJson);
                    }
                    return Map.of(
                            "status", "OK",
                            "path", view.path(),
                            "title", view.title(),
                            "widgetCount", widgetCount(objectMapper, view.layoutJson())
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool addDashboardWidgetTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "add_dashboard_widget";
            }

            @Override
            public String description() {
                return "Append or replace one widget in layout.widgets[] (by id). Args: path (required), "
                        + "widget (id, type, title, x, y, w, h, variableName, valueField, objectPath OR selectionKey). "
                        + "Fine grid columns=84 rowHeight=8: KPI tiles w=21|28 h=14; charts/tables w≥42 h≥28; "
                        + "scada-mimic w=84 h=63; sizes multiples of 7 — never w=2..6 crumbs. "
                        + "For 3+ widgets prefer one set_dashboard_layout layoutJson/template= over many calls. "
                        + "list_variables on device first; columnsJson must be a JSON string.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                Object widgetRaw = arguments.get("widget");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                if (!(widgetRaw instanceof Map<?, ?> widgetMap) || widgetMap.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "widget object is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    DashboardService.DashboardView view = dashboardService.addWidget(
                            path,
                            (Map<String, Object>) widgetMap
                    );
                    return Map.of(
                            "status", "OK",
                            "path", view.path(),
                            "widgetCount", widgetCount(objectMapper, view.layoutJson())
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static int widgetCount(ObjectMapper objectMapper, String layoutJson) {
        return AgentToolResultMetrics.widgetCountFromLayoutJson(objectMapper, layoutJson);
    }

    private static PlatformAgentTool listVariablesTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            VariableMemberAccessService variableMemberAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_variables";
            }

            @Override
            public String description() {
                return "List variables on an object with current values (preview). Args: path (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                PlatformObject node = ObjectTreePort.require(path);
                List<Map<String, Object>> variables = variableMemberAccessService
                        .filterReadable(path, node.variables().values(), auth)
                        .stream()
                        .map(AgentTreeTools::variablePreview)
                        .toList();
                return Map.of("status", "OK", "path", path, "count", variables.size(), "variables", variables);
            }
        };
    }

    private static Map<String, Object> variablePreview(Variable variable) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("name", variable.name());
        preview.put("writable", variable.writable());
        variable.value().ifPresent(record -> {
            if (!record.rows().isEmpty()) {
                preview.put("value", record.firstRow());
            }
        });
        return preview;
    }

    private static PlatformAgentTool setVariableTool(
            ObjectTreePort ObjectTreePort,
            VariableMemberAccessService variableMemberAccessService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "set_variable";
            }

            @Override
            public String description() {
                return "Set variable value on object. Args: path, name, value (string or object map for DataRecord fields).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                var auth = context.authentication();
                PlatformObject node = ObjectTreePort.require(path);
                Variable existing = node.getVariable(name)
                        .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + name));
                variableMemberAccessService.requireWrite(existing, path, auth);
                if (!existing.writable()) {
                    return Map.of("status", "ERROR", "error", "Variable is not writable: " + name);
                }
                Object rawValue = arguments.containsKey("value")
                        ? arguments.get("value")
                        : arguments.get("valueJson");
                if (rawValue == null) {
                    return Map.of("status", "ERROR", "error", "value or valueJson is required");
                }
                try {
                    DataRecord record = toDataRecord(objectMapper, existing, rawValue);
                    Variable updated = ObjectTreePort.setVariableValue(path, name, record);
                    return Map.of(
                            "status", "OK",
                            "path", path,
                            "name", name,
                            "variable", VariableDto.from(updated)
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static DataRecord toDataRecord(ObjectMapper objectMapper, Variable existing, Object rawValue)
            throws Exception {
        if (rawValue instanceof Map<?, ?> map) {
            return DataRecord.single(existing.schema(), (Map<String, Object>) map);
        }
        if (rawValue instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    Map<String, Object> parsed = objectMapper.readValue(trimmed, Map.class);
                    return DataRecord.single(existing.schema(), parsed);
                } catch (Exception ignored) {
                    // fall through to scalar wrap
                }
            }
            return DataRecord.single(existing.schema(), Map.of("value", text));
        }
        return DataRecord.single(existing.schema(), Map.of("value", rawValue));
    }

    private static PlatformAgentTool configureDriverTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            DriverRuntimeService driverRuntimeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_driver";
            }

            @Override
            public String description() {
                return "Configure device driver binding. Args: devicePath, driverId (e.g. snmp), "
                        + "configuration (map or JSON string), pointMappings (map or JSON string), "
                        + "pollIntervalMs?, autoStart? (default true).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String devicePath = stringArg(arguments, "devicePath");
                if (devicePath.isBlank()) {
                    return Map.of("status", "ERROR", "error", "devicePath is required");
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(devicePath, auth);
                try {
                    ObjectTreePort.require(devicePath);
                    Optional<DriverBinding> existingBinding = driverRuntimeService.readBinding(devicePath);
                    Map<String, String> configuration = readStringMap(objectMapper, arguments.get("configuration"));
                    Map<String, String> pointMappings = readStringMap(objectMapper, arguments.get("pointMappings"));
                    if (configuration == null || configuration.isEmpty()) {
                        configuration = existingBinding.map(DriverBinding::configuration).orElse(Map.of());
                    }
                    if (pointMappings == null || pointMappings.isEmpty()) {
                        pointMappings = existingBinding.map(DriverBinding::pointMappings).orElse(Map.of());
                    }
                    String driverId = optionalString(arguments, "driverId");
                    if (driverId == null || driverId.isBlank()) {
                        driverId = existingBinding.map(DriverBinding::driverId)
                                .orElse(DriverBinding.DEFAULT_DRIVER_ID);
                    }
                    int pollIntervalMs = intArg(arguments, "pollIntervalMs", 0);
                    if (pollIntervalMs <= 0) {
                        pollIntervalMs = existingBinding.map(DriverBinding::pollIntervalMs).orElse(5000);
                    }
                    DriverBinding binding = DriverBinding.of(
                            driverId,
                            pollIntervalMs,
                            configuration,
                            pointMappings
                    );
                    driverRuntimeService.configure(devicePath, binding);
                    if (boolArg(arguments, "autoStart", true)) {
                        var status = driverRuntimeService.start(devicePath);
                        return driverStatusMap("OK", status);
                    }
                    return driverRuntimeService.status(devicePath)
                            .map(status -> driverStatusMap("OK", status))
                            .orElse(Map.of("status", "OK", "devicePath", devicePath));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool driverControlTool(
            ObjectAccessService objectAccessService,
            DriverRuntimeService driverRuntimeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "driver_control";
            }

            @Override
            public String description() {
                return "Start/stop/poll/status device driver. Args: devicePath, action (status|start|stop|poll).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String devicePath = stringArg(arguments, "devicePath");
                String action = stringArg(arguments, "action");
                if (devicePath.isBlank() || action.isBlank()) {
                    return Map.of("status", "ERROR", "error", "devicePath and action are required");
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(devicePath, auth);
                try {
                    return switch (action.toLowerCase(Locale.ROOT)) {
                        case "start" -> driverStatusMap("OK", driverRuntimeService.start(devicePath));
                        case "stop" -> driverStatusMap("OK", driverRuntimeService.stop(devicePath));
                        case "poll" -> driverStatusMap("OK", driverRuntimeService.pollNow(devicePath));
                        case "status" -> driverRuntimeService.status(devicePath)
                                .map(status -> driverStatusMap("OK", status))
                                .orElse(Map.of("status", "ERROR", "error", "No driver binding for " + devicePath));
                        default -> Map.of("status", "ERROR", "error", "Unknown action: " + action);
                    };
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> driverStatusMap(
            String status,
            DriverRuntimeService.DriverRuntimeStatus runtimeStatus
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("devicePath", runtimeStatus.devicePath());
        result.put("driverId", runtimeStatus.driverId());
        result.put("driverStatus", runtimeStatus.status());
        result.put("connected", runtimeStatus.connected());
        result.put("pollIntervalMs", runtimeStatus.pollIntervalMs());
        result.put("lastError", runtimeStatus.lastError());
        result.put("telemetryPublishMode", runtimeStatus.telemetryPublishMode());
        result.put("telemetryCoalesceMs", runtimeStatus.telemetryCoalesceMs());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readStringMap(ObjectMapper objectMapper, Object raw) throws Exception {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return out;
        }
        if (raw instanceof String text && !text.isBlank()) {
            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return out;
        }
        throw new IllegalArgumentException("Expected map or JSON string");
    }

    private static PlatformAgentTool validateBundleTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "validate_bundle";
            }

            @Override
            public String description() {
                return "Semantic validation of bundle manifest (no DB writes). Args: appId, manifest (object).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "appId and manifest object are required");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.validateBundle(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool dryRunDeployTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "dry_run_deploy";
            }

            @Override
            public String description() {
                return "Validate bundle and list wouldApply sections. Args: appId, manifest (object).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "appId and manifest object are required");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.dryRunDeploy(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool importPackageTool(
            ObjectMapper objectMapper,
            ApplicationBundleDeployService bundleDeployService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "import_package";
            }

            @Override
            public String description() {
                return "Deploy bundle to platform (mutates DB). Args: packageId, manifest, optional runTests (default true). "
                        + "Requires prior validate_bundle or dry_run_deploy OK for same packageId in this run.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String packageId = stringArg(arguments, "packageId");
                if (packageId.isBlank()) {
                    packageId = stringArg(arguments, "appId");
                }
                Object manifestRaw = arguments.get("manifest");
                if (packageId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "packageId and manifest object are required");
                }
                if (!context.runState().isBundleValidated(packageId)) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "Run validate_bundle or dry_run_deploy with status OK before import_package"
                    );
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                try {
                    boolean runTests = !Boolean.FALSE.equals(arguments.get("runTests"));
                    Map<String, Object> deployed = bundleDeployService.deploy(
                            packageId,
                            parsed,
                            false,
                            true,
                            runTests
                    );
                    if ("OK".equals(String.valueOf(deployed.get("status")))) {
                        deployed.put("status", "OK");
                    } else {
                        deployed.putIfAbsent("status", "ERROR");
                        if (deployed.get("testResults") != null) {
                            deployed.putIfAbsent(
                                    "hint",
                                    "Deploy tests failed — consider rollback_application_deploy for " + packageId
                            );
                        }
                    }
                    return deployed;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
