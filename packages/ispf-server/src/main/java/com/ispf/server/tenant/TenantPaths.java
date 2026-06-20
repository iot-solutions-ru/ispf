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
}
