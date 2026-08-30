package com.ispf.server.tenant;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Trusted authentication details used when a federation tunnel delegates a caller.
 */
public record DelegatedTenantAuthenticationDetails(String tenantId) {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

    public DelegatedTenantAuthenticationDetails {
        String normalized = tenantId == null ? "" : tenantId.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid delegated tenant id");
        }
        tenantId = normalized;
    }
}
