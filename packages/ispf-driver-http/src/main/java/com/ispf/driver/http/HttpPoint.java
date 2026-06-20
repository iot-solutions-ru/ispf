package com.ispf.driver.http;

import java.util.Locale;

/**
 * Point mapping: {@code path}, {@code METHOD:path}, or full URL.
 * Optional suffix {@code :json} marks body should be parsed as JSON number/string when possible.
 */
public record HttpPoint(String method, String url, boolean parseJsonBody) {

    public static HttpPoint parse(String raw, String baseUrl) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("HTTP point mapping is blank");
        }
        String trimmed = raw.trim();
        String method = "GET";
        String target = trimmed;

        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String maybeMethod = trimmed.substring(0, colon).trim().toUpperCase(Locale.ROOT);
            if (isHttpMethod(maybeMethod)) {
                method = maybeMethod;
                target = trimmed.substring(colon + 1).trim();
            }
        }

        boolean parseJson = false;
        if (target.endsWith(":json")) {
            parseJson = true;
            target = target.substring(0, target.length() - 5).trim();
        }

        String url = resolveUrl(target, baseUrl);
        return new HttpPoint(method, url, parseJson);
    }

    private static boolean isHttpMethod(String value) {
        return switch (value) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD" -> true;
            default -> false;
        };
    }

    private static String resolveUrl(String target, String baseUrl) {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.isEmpty()) {
            return target.startsWith("/") ? target : "/" + target;
        }
        if (base.endsWith("/") && target.startsWith("/")) {
            return base.substring(0, base.length() - 1) + target;
        }
        if (!base.endsWith("/") && !target.startsWith("/")) {
            return base + "/" + target;
        }
        return base + target;
    }
}
