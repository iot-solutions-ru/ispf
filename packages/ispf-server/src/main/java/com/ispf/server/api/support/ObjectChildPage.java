package com.ispf.server.api.support;

import java.util.List;
import java.util.regex.Pattern;

/** Page and name-glob helpers for {@code GET /api/v1/objects}. */
public final class ObjectChildPage {

    public static final int MAX_LIMIT = 500;

    private ObjectChildPage() {
    }

    /**
     * Glob on the leaf of {@code path}. {@code *} and {@code ?} are wildcards; other characters are literal.
     * A blank pattern matches every name.
     */
    public static boolean leafMatches(String path, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        String leaf = path == null ? "" : path;
        int dot = leaf.lastIndexOf('.');
        if (dot >= 0 && dot < leaf.length() - 1) {
            leaf = leaf.substring(dot + 1);
        }
        StringBuilder regex = new StringBuilder("^");
        String trimmed = pattern.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(leaf)
                .matches();
    }

    /** When {@code limit} is null the full list is returned. Otherwise a copy of the requested window. */
    public static <T> List<T> slice(List<T> items, Integer offset, Integer limit) {
        if (limit == null) {
            return items;
        }
        int safeLimit = Math.min(MAX_LIMIT, Math.max(1, limit));
        int safeOffset = offset == null ? 0 : Math.max(0, offset);
        if (safeOffset >= items.size()) {
            return List.of();
        }
        int to = Math.min(items.size(), safeOffset + safeLimit);
        return List.copyOf(items.subList(safeOffset, to));
    }
}
