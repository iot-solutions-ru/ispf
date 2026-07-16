package com.ispf.server.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request matching for the embedded web console (all-in-one JAR).
 */
public final class WebConsoleSecurity {

    private WebConsoleSecurity() {
    }

    /**
     * Static assets and SPA navigations that must be reachable without auth
     * so the login page can load from the same origin as the API.
     */
    public static boolean isPublicConsoleRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String path = normalizedPath(request);
        if (path.startsWith("/api/") || path.startsWith("/actuator") || path.startsWith("/ws")) {
            return false;
        }
        return true;
    }

    static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path;
    }
}
