package com.ispf.server.security;

import com.ispf.server.config.IspfAuthorizationRules;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ADR-0073: a principal with claim {@code uiPackAppId} (or {@code ROLE_UI_PACK}) may call
 * BFF invoke for that app, {@code /auth/me}, and logout. Admin and other APIs are rejected
 * even when realm roles include {@code admin}.
 * Registered inside the security chain by {@code SecurityConfig} / {@code LocalSecurityConfig}
 * so the check runs while the security context is still populated.
 */
public class UiPackScopedAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isUiPackScoped(auth)
                && IspfAuthorizationRules.deniesPackScopedApi(request, uiPackAppId(auth))) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "ui-pack scoped token cannot call this API"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    public static boolean isUiPackScoped(Authentication auth) {
        if (!isUser(auth)) {
            return false;
        }
        if (uiPackAppId(auth) != null) {
            return true;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (UiPackScopedAuthority.ROLE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static String uiPackAppId(Authentication auth) {
        if (!isUser(auth)) {
            return null;
        }
        if (auth.getDetails() instanceof UiPackScope scope
                && scope.appId() != null
                && !scope.appId().isBlank()) {
            return scope.appId().trim();
        }
        Jwt jwt = jwtOf(auth);
        if (jwt == null) {
            return null;
        }
        Object claim = jwt.getClaim(UiPackScopedAuthority.CLAIM);
        if (claim == null || claim.toString().isBlank()) {
            return null;
        }
        return claim.toString().trim();
    }

    /**
     * Path allowlist used by tests and {@link IspfAuthorizationRules#isPackScopedPathAllowed}.
     */
    static boolean isAllowedForPack(String path) {
        return IspfAuthorizationRules.isPackScopedPathAllowed(path);
    }

    private static boolean isUser(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static Jwt jwtOf(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return null;
    }
}
