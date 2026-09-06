package com.ispf.driver.graphql;

/**
 * Point mapping for GraphQL reads and optional mutation writes.
 * <p>
 * Formats:
 * <ul>
 *   <li>GraphQL document — starts with {@code {}}, {@code query}, {@code mutation}, or {@code fragment}</li>
 *   <li>Field path — dotted path under {@code data} (for example {@code sensor.temperature}),
 *       optionally prefixed with a document via {@code >>}
 *       ({@code { sensor { temperature } } >> sensor.temperature})</li>
 * </ul>
 */
public record GraphqlPoint(String document, String fieldPath) {

    public static GraphqlPoint parse(String raw, String defaultQuery) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("GraphQL point mapping is blank");
        }
        String trimmed = raw.trim();
        int split = trimmed.indexOf(">>");
        if (split >= 0) {
            String document = trimmed.substring(0, split).trim();
            String path = trimmed.substring(split + 2).trim();
            if (document.isEmpty()) {
                throw new IllegalArgumentException("GraphQL document before >> is blank");
            }
            return new GraphqlPoint(document, path.isEmpty() ? null : path);
        }
        if (looksLikeDocument(trimmed)) {
            return new GraphqlPoint(trimmed, null);
        }
        String query = defaultQuery == null ? "" : defaultQuery.trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException(
                    "Field-path mapping requires configuration query=... or a document>>path mapping");
        }
        return new GraphqlPoint(query, trimmed);
    }

    static boolean looksLikeDocument(String text) {
        if (text.startsWith("{")) {
            return true;
        }
        String lower = text.toLowerCase();
        return lower.startsWith("query")
                || lower.startsWith("mutation")
                || lower.startsWith("fragment");
    }
}
