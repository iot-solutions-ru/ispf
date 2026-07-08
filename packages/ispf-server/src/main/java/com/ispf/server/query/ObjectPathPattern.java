package com.ispf.server.query;

import java.util.regex.Pattern;

/**
 * Glob-style object path matching for tree scans (supports {@code *} and {@code **}).
 */
public final class ObjectPathPattern {

    private ObjectPathPattern() {
    }

    public static boolean matches(String path, String pattern) {
        if (path == null || pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.contains("*")) {
            String regex = "^" + pattern
                    .replace(".", "\\.")
                    .replace("**", "§§")
                    .replace("*", "[^.]*")
                    .replace("§§", ".*") + "$";
            return Pattern.compile(regex).matcher(path).matches();
        }
        return path.equals(pattern) || path.startsWith(pattern);
    }
}
