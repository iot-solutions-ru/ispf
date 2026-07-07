package com.ispf.server.tenant;

public record TenantDraft(String tenantId, String displayName, boolean enabled) {

    public Integer maxDevices() {
        return null;
    }

    public Integer maxObjects() {
        return null;
    }
}
