package com.ispf.server.federation;

public final class FederationPaths {

    public static final String FEDERATION_ROOT = "root.platform.federation";

    private FederationPaths() {
    }

    public static String peerCatalogRoot(String peerName) {
        return FEDERATION_ROOT + "." + slug(peerName);
    }

    public static boolean isCatalogMirrorPath(String path) {
        return path != null
                && (path.equals(FEDERATION_ROOT) || path.startsWith(FEDERATION_ROOT + "."));
    }

    static String slug(String peerName) {
        String normalized = peerName.trim().toLowerCase()
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid peer name for path: " + peerName);
        }
        return normalized;
    }
}
