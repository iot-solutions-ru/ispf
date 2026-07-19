package com.ispf.server.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Set;

public final class IspfRoles {

    public static final String ADMIN = "admin";
    public static final String TENANT_ADMIN = "tenant-admin";
    public static final String DEVELOPER = "developer";
    public static final String OPERATOR = "operator";

    /** Read / execute automation (HMI, invoke, events). */
    public static final String[] ROLES_READ = {OPERATOR, DEVELOPER, ADMIN, TENANT_ADMIN};

    /** Solution configuration (objects, apps, SQL platform tools). */
    public static final String[] ROLES_CONFIG = {DEVELOPER, ADMIN, TENANT_ADMIN};

    /** Platform administration (security globals, cluster, license, …). */
    public static final String[] ROLES_ADMIN = {ADMIN};

    /** Tenant-scoped security administration (users/roles within tenant). */
    public static final String[] ROLES_TENANT_SECURITY = {ADMIN, TENANT_ADMIN};

    private IspfRoles() {
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return extractRoles(authentication).contains(role);
    }

    /** Global platform admin ({@code admin} role). Always bypasses tenant scope. */
    public static boolean isGlobalAdmin(Authentication authentication) {
        return hasRole(authentication, ADMIN);
    }

    /**
     * Alias for {@link #isGlobalAdmin(Authentication)}. Prefer {@code isGlobalAdmin} in new code
     * so tenant-admin is never mistaken for a global bypass.
     */
    public static boolean isAdmin(Authentication authentication) {
        return isGlobalAdmin(authentication);
    }

    public static boolean isTenantAdmin(Authentication authentication) {
        return hasRole(authentication, TENANT_ADMIN);
    }

    public static boolean isDeveloper(Authentication authentication) {
        return hasRole(authentication, DEVELOPER);
    }

    public static boolean isConfigurator(Authentication authentication) {
        return isGlobalAdmin(authentication) || isDeveloper(authentication) || isTenantAdmin(authentication);
    }

    public static Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new HashSet<>();
        if (authentication == null) {
            return roles;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                roles.add(value.substring("ROLE_".length()));
            } else {
                roles.add(value);
            }
        }
        return roles;
    }
}
