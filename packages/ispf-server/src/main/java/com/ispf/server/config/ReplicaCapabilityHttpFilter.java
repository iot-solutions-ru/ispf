package com.ispf.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ADR-0032: reject HTTP traffic this replica is not configured to serve.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ReplicaCapabilityHttpFilter extends OncePerRequestFilter {

    private final ClusterProperties clusterProperties;

    public ReplicaCapabilityHttpFilter(ClusterProperties clusterProperties) {
        this.clusterProperties = clusterProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!clusterProperties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return isBaselinePath(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!clusterProperties.isHttpPublicActive() && requiresHttpPublic(path, method)) {
            reject(response, "Replica profile does not serve public HTTP API");
            return;
        }
        if (!clusterProperties.isConfigWriteActive() && isConfigMutation(path, method)) {
            reject(response, "Replica profile is read-only (config-write disabled)");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isBaselinePath(String path) {
        return path.equals("/api/v1/info")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/health");
    }

    private static boolean requiresHttpPublic(String path, String method) {
        if (path.startsWith("/api/v1/platform/jobs/") && HttpMethod.GET.matches(method)) {
            return false;
        }
        return path.startsWith("/api/")
                || path.startsWith("/actuator/");
    }

    private static boolean isConfigMutation(String path, String method) {
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
            return false;
        }
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/config")) {
            return false;
        }
        if (path.startsWith("/api/v1/reports/by-path/run")
                || path.startsWith("/api/v1/events/")
                || path.startsWith("/api/v1/bff/")
                || path.startsWith("/api/v1/workflows/")
                || path.startsWith("/api/v1/objects/by-path/functions/invoke")) {
            return false;
        }
        return path.startsWith("/api/v1/");
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"REPLICA_CAPABILITY_DENIED\",\"message\":\"" + message + "\"}");
    }
}
