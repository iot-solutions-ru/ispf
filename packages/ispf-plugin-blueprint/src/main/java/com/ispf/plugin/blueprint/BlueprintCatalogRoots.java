package com.ispf.plugin.blueprint;

/**
 * Tree paths for the three model catalog kinds (blueprint views).
 */
public final class BlueprintCatalogRoots {

    public static final String RELATIVE = "root.platform.relative-blueprints";
    public static final String INSTANCE = "root.platform.instance-types";
    public static final String ABSOLUTE = "root.platform.absolute-blueprints";
    /** Legacy catalog path — removed at startup migration. */
    public static final String LEGACY = "root.platform.blueprints";
    public static final String INSTANCES = "root.platform.instances";

    private BlueprintCatalogRoots() {
    }

    public static String catalogRoot(BlueprintType type) {
        return switch (type) {
            case RELATIVE -> RELATIVE;
            case INSTANCE -> INSTANCE;
            case ABSOLUTE -> ABSOLUTE;
        };
    }

    public static String catalogTitle(BlueprintType type) {
        return switch (type) {
            case RELATIVE -> "Relative Blueprints";
            case INSTANCE -> "Instance Types";
            case ABSOLUTE -> "Absolute Blueprints";
        };
    }

    public static boolean isCatalogPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals(RELATIVE) || path.startsWith(RELATIVE + ".")
                || path.equals(INSTANCE) || path.startsWith(INSTANCE + ".")
                || path.equals(ABSOLUTE) || path.startsWith(ABSOLUTE + ".");
    }

    public static boolean isLegacyPath(String path) {
        return path != null
                && (path.equals(LEGACY) || path.startsWith(LEGACY + "."));
    }

    public static boolean isDefinitionPath(String path) {
        return isCatalogPath(path) && !path.equals(RELATIVE) && !path.equals(INSTANCE)
                && !path.equals(ABSOLUTE);
    }
}
