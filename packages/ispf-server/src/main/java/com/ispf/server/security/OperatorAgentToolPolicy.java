package com.ispf.server.security;

import com.ispf.server.security.OperatorAgentToolAllowlist;
import com.ispf.server.config.IspfRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BL-179: resolves effective operator-agent tool allowlist from user roles and role templates.
 */
@Service
public class OperatorAgentToolPolicy {

    public Set<String> allowedTools(Authentication authentication) {
        Set<String> roles = extractRoles(authentication);
        if (roles.contains(IspfRoles.ADMIN) || roles.contains(IspfRoles.DEVELOPER)) {
            return Set.copyOf(OperatorAgentToolAllowlist.ALLOWED_TOOLS);
        }
        boolean hasTemplateRole = roles.stream().anyMatch(PlatformRoleTemplatePermissions::isTemplate);
        if (!hasTemplateRole) {
            return Set.copyOf(OperatorAgentToolAllowlist.ALLOWED_TOOLS);
        }
        return PlatformRoleTemplatePermissions.unionOperatorAgentTools(roles);
    }

    private static Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication == null) {
            return roles;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                roles.add(value.substring("ROLE_".length()));
            }
        }
        return roles;
    }
}
