package com.ispf.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Simulates Keycloak roles in local profile via {@code X-ISPF-Role: admin|operator}.
 */
public class LocalRoleHeaderFilter extends OncePerRequestFilter {

    public static final String ROLE_HEADER = "X-ISPF-Role";

    private final IspfSecurityProperties properties;

    public LocalRoleHeaderFilter(IspfSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()
                && !(existing instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String role = request.getHeader(ROLE_HEADER);
        if (role == null || role.isBlank()) {
            role = properties.getLocalDefaultRole();
        }
        if (role == null || role.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        role = role.trim().toLowerCase();
        if (IspfRoles.ADMIN.equals(role) || IspfRoles.OPERATOR.equals(role)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    role,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
