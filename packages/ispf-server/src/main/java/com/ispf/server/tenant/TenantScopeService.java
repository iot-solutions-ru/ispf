package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TenantScopeService {

    private final TenantStore tenantStore;

    public TenantScopeService(TenantStore tenantStore) {
        this.tenantStore = tenantStore;
    }

    public Optional<String> resolveTenantId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (isAdmin(authentication)) {
            return Optional.empty();
        }
        return tenantStore.findTenantIdForUser(authentication.getName());
    }

    public boolean isPathVisible(String path, Authentication authentication) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Optional<String> tenantId = resolveTenantId(authentication);
        if (tenantId.isEmpty()) {
            return true;
        }
        String scopedPrefix = TenantPaths.tenantRoot(tenantId.get());
        return path.equals("root")
                || path.equals(TenantPaths.TENANTS_ROOT)
                || path.equals(scopedPrefix)
                || path.startsWith(scopedPrefix + ".");
    }

    private static boolean isAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (("ROLE_" + IspfRoles.ADMIN).equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
