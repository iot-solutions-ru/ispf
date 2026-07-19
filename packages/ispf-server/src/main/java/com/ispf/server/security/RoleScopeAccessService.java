package com.ispf.server.security;

import com.ispf.server.config.IspfRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Enforces ISA-95 / role-template {@code scopePathPrefixes} on REST object access (BL-157).
 * When the principal has one or more template roles with scopes, object paths must match the union.
 * Admin and users without template scopes are unrestricted by this layer (object ACL still applies).
 */
@Service
public class RoleScopeAccessService {

    public boolean isPathInRoleScope(String objectPath, Authentication authentication) {
        if (objectPath == null || objectPath.isBlank()) {
            return false;
        }
        if (IspfRoles.isAdmin(authentication) || IspfRoles.isDeveloper(authentication)) {
            return true;
        }
        Set<String> prefixes = resolveScopePrefixes(authentication);
        if (prefixes.isEmpty()) {
            return true;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (objectPath.equals(prefix)
                    || objectPath.startsWith(prefix + ".")
                    || prefix.startsWith(objectPath + ".")
                    || "root".equals(objectPath)
                    || "root.platform".equals(objectPath)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> resolveScopePrefixes(Authentication authentication) {
        Set<String> roles = extractRoles(authentication);
        Set<String> prefixes = new LinkedHashSet<>();
        for (String role : roles) {
            PlatformRoleTemplatePermissions.scopePathPrefixes(role).ifPresent(prefixes::addAll);
        }
        return prefixes;
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
