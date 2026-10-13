package com.ispf.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ADR-0073 Wave 2A: pack-scoped principals may call BFF (+ auth/me) only — not admin APIs.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 40)
public class UiPackScopedAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && isUiPackScoped(auth)) {
            String path = request.getRequestURI();
            if (!isAllowedForPack(path)) {
                response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "ui-pack scoped token cannot call admin APIs (ADR-0073); use BFF invoke"
                );
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    static boolean isUiPackScoped(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (UiPackScopedAuthority.ROLE.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    static boolean isAllowedForPack(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/v1/bff/")) {
            return true;
        }
        if (path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/logout")) {
            return true;
        }
        if (path.equals("/api/v1/info") || path.startsWith("/actuator/health")) {
            return true;
        }
        return false;
    }
}
