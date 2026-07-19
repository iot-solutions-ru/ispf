package com.ispf.server.tenant;

/**
 * Result of {@link TenantService#createTenant(TenantDraft)} including one-time local admin credentials.
 */
public record TenantCreateResult(
        Tenant tenant,
        String adminUsername,
        String adminPassword
) {
}
