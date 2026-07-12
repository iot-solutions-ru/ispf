package com.ispf.server.ref;

import com.ispf.core.ref.PlatformRef;

/**
 * Resolves {@code @} in {@link PlatformRef} to the rule-owner object path.
 */
public final class PlatformRefResolver {

    private PlatformRefResolver() {
    }

    public static PlatformRef resolve(PlatformRef ref, String ruleObjectPath) {
        if (ref == null) {
            throw new IllegalArgumentException("ref is required");
        }
        return ref.resolveObject(ruleObjectPath);
    }

    public static String resolveObjectPath(PlatformRef ref, String ruleObjectPath) {
        return resolve(ref, ruleObjectPath).object();
    }
}
