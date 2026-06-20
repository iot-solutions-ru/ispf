package com.ispf.server.tenant;

import java.time.Instant;

public record Tenant(
        String tenantId,
        String displayName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public String objectPath() {
        return TenantPaths.tenantRoot(tenantId);
    }

    public String platformPath() {
        return TenantPaths.tenantPlatform(tenantId);
    }
}
