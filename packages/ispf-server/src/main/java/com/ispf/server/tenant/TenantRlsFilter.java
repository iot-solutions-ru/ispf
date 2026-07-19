package com.ispf.server.tenant;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.TenantIsolationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Sets {@link TenantRlsContext} after authentication is available (BL-155 RLS).
 * Registered in the security filter chain after bearer / JWT filters.
 */
public class TenantRlsFilter extends OncePerRequestFilter {

    private final TenantScopeService tenantScopeService;
    private final TenantIsolationProperties isolationProperties;

    public TenantRlsFilter(
            TenantScopeService tenantScopeService,
            TenantIsolationProperties isolationProperties
    ) {
        this.tenantScopeService = tenantScopeService;
        this.isolationProperties = isolationProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isolationProperties.isDbRowIsolation()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            TenantRlsContext.set(resolveState());
            filterChain.doFilter(request, response);
        } finally {
            TenantRlsContext.clear();
        }
    }

    TenantRlsContext.State resolveState() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Health / bootstrap / unauthenticated — default allow (matches unset GUC policy).
            return TenantRlsContext.State.allowAll();
        }
        if (IspfRoles.isGlobalAdmin(authentication)) {
            return TenantRlsContext.State.allowAll();
        }
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        if (tenantId.isPresent()) {
            return TenantRlsContext.State.forTenant(tenantId.get());
        }
        // Authenticated non-admin without tenant assignment: API scope is unrestricted.
        return TenantRlsContext.State.allowAll();
    }
}
