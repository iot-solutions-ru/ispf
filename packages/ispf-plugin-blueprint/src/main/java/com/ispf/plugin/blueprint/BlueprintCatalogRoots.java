package com.ispf.plugin.blueprint;

/**
 * Tree paths for the three blueprint catalog kinds.
 */
public final class BlueprintCatalogRoots {

    public static final String MIXIN = "root.platform.mixin-blueprints";
    public static final String INSTANCE = "root.platform.instance-types";
    public static final String SINGLETON = "root.platform.singleton-blueprints";
    /** Legacy catalog path — removed at startup migration. */
    public static final String LEGACY = "root.platform.blueprints";
    public static final String INSTANCES = "root.platform.instances";

    private BlueprintCatalogRoots() {
    }

    public static String catalogRoot(BlueprintType type) {
        return switch (type) {
            case MIXIN -> MIXIN;
            case INSTANCE -> INSTANCE;
            case SINGLETON -> SINGLETON;
        };
    }

    public static String catalogTitle(BlueprintType type) {
        return switch (type) {
            case MIXIN -> "Mixin Blueprints";
            case INSTANCE -> "Instance Types";
            case SINGLETON -> "Singleton Blueprints";
        };
    }

    public static boolean isCatalogPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals(MIXIN) || path.startsWith(MIXIN + ".")
                || path.equals(INSTANCE) || path.startsWith(INSTANCE + ".")
                || path.equals(SINGLETON) || path.startsWith(SINGLETON + ".");
    }

    public static boolean isLegacyPath(String path) {
        return path != null
                && (path.equals(LEGACY) || path.startsWith(LEGACY + "."));
    }

    /**
     * Definition-only catalog children (MIXIN / INSTANCE). SINGLETON children are live
     * application objects under {@link #SINGLETON}, not definition stubs.
     */
    public static boolean isDefinitionPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith(MIXIN + ".") || path.startsWith(INSTANCE + ".");
    }
}
