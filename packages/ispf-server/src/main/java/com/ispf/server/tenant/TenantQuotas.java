package com.ispf.server.tenant;

public record TenantQuotas(Integer maxDevices, Integer maxObjects) {

    public static TenantQuotas unlimited() {
        return new TenantQuotas(null, null);
    }
}
