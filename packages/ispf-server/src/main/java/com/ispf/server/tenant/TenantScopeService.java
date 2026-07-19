package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantScopeService {

    private final TenantStore tenantStore;
    private final TenantIsolationProperties isolationProperties;
    private final TenantIsolationValidator isolationValidator;
    private final ConcurrentHashMap<String, Optional<String>> tenantIdByUser = new ConcurrentHashMap<>();

    public TenantScopeService(
            TenantStore tenantStore,
            TenantIsolationProperties isolationProperties,
            TenantIsolationValidator isolationValidator
    ) {
        this.tenantStore = tenantStore;
        this.isolationProperties = isolationProperties;
        this.isolationValidator = isolationValidator;
    }

    public Optional<String> resolveTenantId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (isAdmin(authentication)) {
            return Optional.empty();
        }
        Optional<String> fromClaim = resolveOidcTenantClaim(authentication);
        if (fromClaim.isPresent()) {
            return fromClaim;
        }
        String username = authentication.getName();
        return tenantIdByUser.computeIfAbsent(username, tenantStore::findTenantIdForUser);
    }

    /**
     * OIDC / JWT tenant claim mapping (BL-155). Claim name from {@code ispf.tenant.oidc-tenant-claim}.
     */
    public Optional<String> resolveOidcTenantClaim(Authentication authentication) {
        String claimName = isolationProperties.getOidcTenantClaim();
        if (claimName == null || claimName.isBlank()) {
            return Optional.empty();
        }
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            return Optional.empty();
        }
        Object raw = jwt.getClaim(claimName);
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = isolationValidator.normalizeOidcTenantClaim(String.valueOf(raw));
        return Optional.ofNullable(normalized);
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

    public void requirePathInScope(String path, Authentication authentication) {
        if (!isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
    }

    public void invalidateUserCache(String username) {
        if (username != null && !username.isBlank()) {
            tenantIdByUser.remove(username);
        }
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
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
