package com.ispf.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Serves the React SPA from {@code classpath:/static} for deep links when the
 * web console is embedded in the all-in-one JAR.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class WebConsoleSpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!WebConsoleSecurity.isPublicConsoleRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = WebConsoleSecurity.normalizedPath(request);
        if ("/".equals(path) || "/index.html".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (hasFileExtension(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Resource resource = new ClassPathResource("static" + path);
        if (resource.exists() && resource.isReadable()) {
            filterChain.doFilter(request, response);
            return;
        }

        request.getRequestDispatcher("/index.html").forward(request, response);
    }

    private static boolean hasFileExtension(String path) {
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        return last.contains(".");
    }
}
