package com.ispf.server.application.uipack;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ADR-0054: serve installed UI packs at {@code GET /apps/<appId>/**} with SPA fallback.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class HostedUiPackFilter extends OncePerRequestFilter {

    private final DropInUiPackLoader uiPackLoader;

    public HostedUiPackFilter(DropInUiPackLoader uiPackLoader) {
        this.uiPackLoader = uiPackLoader;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        String path = normalizedPath(request);
        return !path.startsWith("/apps/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = normalizedPath(request);
        // /apps/<appId>/...
        String rest = path.substring("/apps/".length());
        int slash = rest.indexOf('/');
        String appId = slash < 0 ? rest : rest.substring(0, slash);
        String relative = slash < 0 ? "" : rest.substring(slash + 1);
        if (appId == null || appId.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (HostedUiPackOperatorAgentInjector.isPlatformAsset(appId, relative)) {
            servePlatformWidget(request, response);
            return;
        }
        if (!uiPackLoader.isPackInstalled(appId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "UI pack not installed: " + appId);
            return;
        }
        Path asset;
        try {
            asset = uiPackLoader.resolveAsset(appId, relative);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        if (asset == null || !Files.isRegularFile(asset)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = probeContentType(asset);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        boolean html = contentType != null && contentType.toLowerCase().contains("html");
        if (html) {
            String htmlBody = Files.readString(asset, StandardCharsets.UTF_8);
            String injected = HostedUiPackOperatorAgentInjector.inject(htmlBody, appId);
            byte[] bytes = injected.getBytes(StandardCharsets.UTF_8);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentLength(bytes.length);
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            try (OutputStream out = response.getOutputStream()) {
                out.write(bytes);
            }
            return;
        }
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setContentLengthLong(Files.size(asset));
            return;
        }
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(asset, out);
        }
    }

    private void servePlatformWidget(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/uipack-platform/" + HostedUiPackOperatorAgentInjector.WIDGET_FILE
        )) {
            if (in == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            byte[] bytes = in.readAllBytes();
            response.setContentType("text/javascript; charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(bytes.length);
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            try (OutputStream out = response.getOutputStream()) {
                out.write(bytes);
            }
        }
    }

    private static String probeContentType(Path asset) throws IOException {
        String probed = Files.probeContentType(asset);
        if (probed != null && !probed.isBlank()) {
            return probed;
        }
        String name = asset.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return MediaType.TEXT_HTML_VALUE;
        }
        if (name.endsWith(".js") || name.endsWith(".mjs")) {
            return "text/javascript";
        }
        if (name.endsWith(".css")) {
            return "text/css";
        }
        if (name.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (name.endsWith(".woff2")) {
            return "font/woff2";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private static String normalizedPath(HttpServletRequest request) {
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
