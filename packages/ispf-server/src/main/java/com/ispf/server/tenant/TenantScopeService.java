package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
        // Only global admin bypasses tenant scope. tenant-admin never bypasses.
        if (IspfRoles.isGlobalAdmin(authentication)) {
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

    public Optional<String> tenantRootPrefix(Authentication authentication) {
        return resolveTenantId(authentication).map(TenantPaths::tenantRoot);
    }

    public void requireTenantAdminOf(String tenantId, Authentication authentication) {
        if (IspfRoles.isGlobalAdmin(authentication)) {
            return;
        }
        if (!IspfRoles.isTenantAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant admin access required");
        }
        String normalized = tenantId == null ? "" : tenantId.trim().toLowerCase();
        String callerTenant = resolveTenantId(authentication)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tenant admin requires tenant_id assignment"
                ));
        if (!callerTenant.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + normalized);
        }
    }

    public boolean isPathVisible(String path, Authentication authentication) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Optional<String> tenantId = resolveTenantId(authentication);
        if (tenantId.isEmpty()) {
            return true;
        }
        // Sole-tenant / white-label: clients speak root.platform.*; expand before scope check.
        String canonical = TenantVirtualRoot.toCanonical(path.trim(), tenantId.get());
        String platform = TenantPaths.tenantPlatform(tenantId.get());
        // Navigation stubs (root.tenant / root.tenant.{id}) are hidden — only root + platform subtree.
        return "root".equals(canonical)
                || platform.equals(canonical)
                || canonical.startsWith(platform + ".");
    }

    public void requirePathInScope(String path, Authentication authentication) {
        if (!isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
    }

    /**
     * Expand virtual sole-tenant paths ({@code root.platform.*}) to storage paths for the caller.
     * No-op for global admin / unscoped callers.
     */
    public String toCanonicalPath(String path, Authentication authentication) {
        return resolveTenantId(authentication)
                .map(tenantId -> TenantVirtualRoot.toCanonical(path, tenantId))
                .orElse(path);
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
}
