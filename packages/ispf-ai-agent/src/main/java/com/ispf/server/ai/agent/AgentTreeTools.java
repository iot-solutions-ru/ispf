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
import com.ispf.server.query.ObjectQueryCatalog;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.ObjectAclStore;
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

    /** Map common agent aliases (OPERATOR, ROLE_ADMIN) onto platform role ids. */
    static String canonicalizeRoleName(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        String lower = normalized.toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (lower) {
            case "operator", "operators", "ops", "hmi", "user" -> com.ispf.server.config.IspfRoles.OPERATOR;
            case "admin", "administrator", "admins" -> com.ispf.server.config.IspfRoles.ADMIN;
            case "developer", "dev", "developers", "configurator" -> com.ispf.server.config.IspfRoles.DEVELOPER;
            case "tenant-admin", "tenantadmin", "tenant_admin" -> com.ispf.server.config.IspfRoles.TENANT_ADMIN;
            case "operator-readonly", "operatorreadonly", "readonly" -> "operator-readonly";
            default -> lower;
        };
    }

        String normalized = role.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        String lower = normalized.toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (lower) {
            case "operator", "operators", "hmi", "ops", "user" -> com.ispf.server.config.IspfRoles.OPERATOR;
            case "admin", "administrator", "admins" -> com.ispf.server.config.IspfRoles.ADMIN;
            case "developer", "dev", "developers", "configurator" -> com.ispf.server.config.IspfRoles.DEVELOPER;
            case "tenant-admin", "tenantadmin", "tenant_admin" -> com.ispf.server.config.IspfRoles.TENANT_ADMIN;
            case "operator-readonly", "operatorreadonly", "readonly" -> "operator-readonly";
            default -> lower;
        };
    }

    /**
     * Final ship gate. Interactive validate may leave {@code LOGIC_HOST_DEVICE} as WARNING;
     * import refuses to apply until ship-canon validation is OK.
     *
     * @return null when the manifest may ship
     */
    private static Map<String, Object> shipCanonGate(
            AiToolRegistry aiToolRegistry,
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            String actor
    ) {
        Map<String, Object> ship = aiToolRegistry.validateBundle(appId, manifest, actor, true);
        if (BundleValidationResult.OK.equals(ship.get("status"))) {
            return null;
        }
        Map<String, Object> blocked = new LinkedHashMap<>(ship);
        blocked.put("status", BundleValidationResult.ERROR);
        blocked.putIfAbsent("error", "Ship canon validation failed");
        blocked.put("shipCanon", true);
        return blocked;
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
