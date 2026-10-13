package com.ispf.server.config;

import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.UiPackScope;
import com.ispf.server.security.UiPackScopedAuthority;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authenticates local profile requests via {@code Authorization: Bearer <token>}.
 */
public class LocalBearerTokenFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformUserService userService;

    public LocalBearerTokenFilter(PlatformUserService userService) {
        this.userService = userService;
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

        String token = null;
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            token = header.substring(BEARER_PREFIX.length()).trim();
        }
        if (token != null && !token.isBlank()) {
            userService.authenticateToken(token).ifPresent(user -> {
                Set<String> resolvedRoles = new LinkedHashSet<>();
                if (!user.roles().isEmpty()) {
                    resolvedRoles.add(IspfRoles.OPERATOR);
                }
                resolvedRoles.addAll(user.roles());
                List<SimpleGrantedAuthority> authorities = new ArrayList<>(resolvedRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
                String packApp = user.uiPackAppId();
                if (packApp != null && !packApp.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(UiPackScopedAuthority.ROLE));
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        user.username(),
                        null,
                        authorities
                );
                if (packApp != null && !packApp.isBlank()) {
                    authentication.setDetails(new UiPackScope(packApp.trim()));
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
