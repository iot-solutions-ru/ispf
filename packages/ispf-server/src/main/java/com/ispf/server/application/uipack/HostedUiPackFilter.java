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
import java.io.OutputStream;
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
        if (!uiPackLoader.isPackInstalled(appId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "UI pack not installed: " + appId);
            return;
        }
        String canonicalAppId = String.valueOf(uiPackLoader.packSummary(appId).getOrDefault("appId", "")).trim();
        // Directory alias (e.g. oil-control-ui → oil-control): Vite basePath is /apps/<appId>/.
        if (!canonicalAppId.isEmpty() && !canonicalAppId.equals(appId) && isSafeAppId(canonicalAppId)) {
            String suffix = slash < 0 ? "/" : rest.substring(slash);
            if (suffix.isEmpty()) {
                suffix = "/";
            }
            String location = "/apps/" + canonicalAppId + suffix;
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                location = location + "?" + query;
            }
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", location);
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
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setContentLengthLong(Files.size(asset));
            return;
        }
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(asset, out);
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

    private static boolean isSafeAppId(String appId) {
        if (appId.length() > 64) {
            return false;
        }
        for (int i = 0; i < appId.length(); i++) {
            char c = appId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return !appId.contains("..");
    }
}
