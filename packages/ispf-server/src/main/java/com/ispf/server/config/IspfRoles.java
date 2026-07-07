package com.ispf.server.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Set;

public final class IspfRoles {

    public static final String ADMIN = "admin";
    public static final String DEVELOPER = "developer";
    public static final String OPERATOR = "operator";

    /** Read / execute automation (HMI, invoke, events). */
    public static final String[] ROLES_READ = {OPERATOR, DEVELOPER, ADMIN};

    /** Solution configuration (objects, apps, SQL platform tools). */
    public static final String[] ROLES_CONFIG = {DEVELOPER, ADMIN};

    /** Platform administration (security, cluster, license, …). */
    public static final String[] ROLES_ADMIN = {ADMIN};

    private IspfRoles() {
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return extractRoles(authentication).contains(role);
    }

    public static boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, ADMIN);
    }

    public static boolean isDeveloper(Authentication authentication) {
        return hasRole(authentication, DEVELOPER);
    }

    public static boolean isConfigurator(Authentication authentication) {
        return isAdmin(authentication) || isDeveloper(authentication);
    }

    private static Set<String> extractRoles(Authentication authentication) {
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
