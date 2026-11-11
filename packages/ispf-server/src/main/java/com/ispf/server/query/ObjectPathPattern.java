package com.ispf.server.query;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Glob-style object path matching for tree scans (supports {@code *} and {@code **}).
 */
public final class ObjectPathPattern {

    private static final int MAX_COMPILED_PATTERNS = 4_096;
    private static final ConcurrentHashMap<String, Pattern> COMPILED_CACHE = new ConcurrentHashMap<>();

    private ObjectPathPattern() {
    }

    public static boolean matches(String path, String pattern) {
        if (path == null || pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.contains("*")) {
            return compileGlob(pattern).matcher(path).matches();
        }
        return path.equals(pattern) || path.startsWith(pattern);
    }

    /**
     * Extracts the literal prefix preceding the first {@code *} wildcard, or the entire pattern
     * when no wildcard is present. Used to range-scan {@link com.ispf.core.object.ObjectTree}
     * instead of walking all nodes.
     */
    public static String staticPrefix(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        int star = pattern.indexOf('*');
        return star < 0 ? pattern : pattern.substring(0, star);
    }

    private static Pattern compileGlob(String pattern) {
        Pattern cached = COMPILED_CACHE.get(pattern);
        if (cached != null) {
            return cached;
        }
        StringBuilder regex = new StringBuilder(pattern.length() + 16);
        regex.append('^');
        int i = 0;
        int n = pattern.length();
        while (i < n) {
            int star = pattern.indexOf('*', i);
            if (star < 0) {
                regex.append(Pattern.quote(pattern.substring(i)));
                break;
            }
            if (star > i) {
                regex.append(Pattern.quote(pattern.substring(i, star)));
            }
            if (star + 1 < n && pattern.charAt(star + 1) == '*') {
                regex.append(".*");
                i = star + 2;
                while (i < n && pattern.charAt(i) == '*') {
                    i++;
                }
            } else {
                regex.append("[^.]*");
                i = star + 1;
            }
        }
        regex.append('$');
        Pattern compiled = Pattern.compile(regex.toString());
        if (COMPILED_CACHE.size() < MAX_COMPILED_PATTERNS) {
            COMPILED_CACHE.putIfAbsent(pattern, compiled);
        }
        return compiled;
    }
}
