package com.ispf.driver.odata;

/** OData point mapping: Sensors | Sensors#Temperature | Sensors(1)/Name */
public record OdataPoint(String path, String property) {

    public static OdataPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("OData point mapping is blank");
        }
        String trimmed = raw.trim();
        int hash = trimmed.indexOf('#');
        if (hash >= 0) {
            String path = trimmed.substring(0, hash).trim();
            String property = trimmed.substring(hash + 1).trim();
            if (path.isEmpty() || property.isEmpty()) {
                throw new IllegalArgumentException("OData mapping requires path#property: " + raw);
            }
            return new OdataPoint(normalizePath(path), property);
        }
        return new OdataPoint(normalizePath(trimmed), null);
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    public boolean hasProperty() {
        return property != null && !property.isBlank();
    }
}
