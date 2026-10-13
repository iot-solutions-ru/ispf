package com.ispf.server.application.bundle;

/**
 * Bundle manifest <em>format</em> version (ADR-0064) — not the product {@code version} semver.
 * Omit {@code formatVersion} in JSON to mean {@link #CURRENT}.
 */
public final class BundleFormatVersions {

    /** Current wire format written by pull-from-tree and accepted without migration. */
    public static final int CURRENT = 1;

    /** Oldest format this platform build can validate/deploy. */
    public static final int MIN_SUPPORTED = 1;

    /** Newest format this platform build can validate/deploy. */
    public static final int MAX_SUPPORTED = 1;

    private BundleFormatVersions() {
    }

    /** Treat null / absent as {@link #CURRENT}. */
    public static int resolve(Integer formatVersion) {
        return formatVersion == null ? CURRENT : formatVersion;
    }

    public static boolean isSupported(int formatVersion) {
        return formatVersion >= MIN_SUPPORTED && formatVersion <= MAX_SUPPORTED;
    }
}
