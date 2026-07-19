package com.ispf.server.tenant;

/**
 * Bidirectional path rewrite for sole-tenant / white-label UX.
 * <p>
 * Storage remains {@code root.tenant.{id}.platform.*}. For callers with a tenant id,
 * the REST/WS surface presents that subtree as {@code root.platform.*} (and {@code root}
 * as the sole parent of {@code root.platform}).
 */
public final class TenantVirtualRoot {

    public static final String VIRTUAL_PLATFORM = "root.platform";

    private TenantVirtualRoot() {
    }

    /**
     * Expand a client/virtual path to the canonical storage path.
     * Already-canonical tenant paths and {@code root} are left unchanged.
     */
    public static String toCanonical(String path, String tenantId) {
        if (path == null || path.isBlank() || tenantId == null || tenantId.isBlank()) {
            return path;
        }
        String normalized = path.trim();
        if ("root".equals(normalized)) {
            return normalized;
        }
        if (VIRTUAL_PLATFORM.equals(normalized) || normalized.startsWith(VIRTUAL_PLATFORM + ".")) {
            String suffix = VIRTUAL_PLATFORM.equals(normalized)
                    ? ""
                    : normalized.substring(VIRTUAL_PLATFORM.length());
            return TenantPaths.tenantPlatform(tenantId) + suffix;
        }
        return normalized;
    }

    /**
     * Collapse a canonical storage path to the virtual sole-tenant path.
     * Returns {@code null} when the path should be hidden from the tenant surface
     * (e.g. {@code root.tenant} / {@code root.tenant.{id}} navigation stubs).
     */
    public static String toVirtual(String path, String tenantId) {
        if (path == null || path.isBlank() || tenantId == null || tenantId.isBlank()) {
            return path;
        }
        String normalized = path.trim();
        if ("root".equals(normalized)) {
            return normalized;
        }
        String platform = TenantPaths.tenantPlatform(tenantId);
        if (platform.equals(normalized)) {
            return VIRTUAL_PLATFORM;
        }
        if (normalized.startsWith(platform + ".")) {
            return VIRTUAL_PLATFORM + normalized.substring(platform.length());
        }
        String tenantRoot = TenantPaths.tenantRoot(tenantId);
        if (TenantPaths.TENANTS_ROOT.equals(normalized)
                || tenantRoot.equals(normalized)) {
            return null;
        }
        // Already virtual or outside tenant world — leave as-is (caller filters visibility).
        return normalized;
    }

    public static boolean isVirtualPlatformPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.trim();
        return VIRTUAL_PLATFORM.equals(normalized) || normalized.startsWith(VIRTUAL_PLATFORM + ".");
    }

    public static String virtualDataSourcesRoot() {
        return VIRTUAL_PLATFORM + ".data-sources";
    }

    public static String canonicalDataSourcesRoot(String tenantId) {
        return TenantPaths.tenantPlatform(tenantId) + ".data-sources";
    }
}
