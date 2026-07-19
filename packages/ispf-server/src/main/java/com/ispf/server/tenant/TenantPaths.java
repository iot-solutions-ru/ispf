package com.ispf.server.tenant;

public final class TenantPaths {

    public static final String TENANTS_ROOT = "root.tenant";

    private TenantPaths() {
    }

    public static String tenantRoot(String tenantId) {
        return TENANTS_ROOT + "." + tenantId;
    }

    public static String tenantPlatform(String tenantId) {
        return tenantRoot(tenantId) + ".platform";
    }

    public static String tenantDevices(String tenantId) {
        return tenantPlatform(tenantId) + ".devices";
    }

    public static String tenantDashboards(String tenantId) {
        return tenantPlatform(tenantId) + ".dashboards";
    }

    public static String tenantSecurity(String tenantId) {
        return tenantPlatform(tenantId) + ".security";
    }

    public static String tenantUsers(String tenantId) {
        return tenantSecurity(tenantId) + ".users";
    }

    public static String tenantRoles(String tenantId) {
        return tenantSecurity(tenantId) + ".roles";
    }

    public static String tenantUserPath(String tenantId, String username) {
        return tenantUsers(tenantId) + "." + username;
    }

    public static String tenantRolePath(String tenantId, String roleName) {
        return tenantRoles(tenantId) + "." + roleName;
    }

    /**
     * Remap a global {@code root.platform.*} prefix to the tenant platform tree when the caller is tenant-scoped.
     */
    public static String remapPlatformPrefix(String globalPrefix, String tenantId) {
        if (globalPrefix == null || tenantId == null || tenantId.isBlank()) {
            return globalPrefix;
        }
        if ("root.platform".equals(globalPrefix) || globalPrefix.startsWith("root.platform.")) {
            return tenantPlatform(tenantId) + globalPrefix.substring("root.platform".length());
        }
        return globalPrefix;
    }
}
