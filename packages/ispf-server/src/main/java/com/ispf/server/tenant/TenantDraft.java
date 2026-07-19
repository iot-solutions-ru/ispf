package com.ispf.server.tenant;

public record TenantDraft(
        String tenantId,
        String displayName,
        boolean enabled,
        String adminUsername,
        String adminPassword,
        String adminDisplayName
) {

    public TenantDraft(String tenantId, String displayName, boolean enabled) {
        this(tenantId, displayName, enabled, null, null, null);
    }

    public Integer maxDevices() {
        return null;
    }

    public Integer maxObjects() {
        return null;
    }
}
