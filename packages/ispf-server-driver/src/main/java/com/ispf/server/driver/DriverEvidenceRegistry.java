package com.ispf.server.driver;

import java.util.Set;

/**
 * Plant-gate allowlist. Empty on purpose: a loopback test does not make a driver field-proven.
 * Add an id only together with the named plant record that earned {@link DriverEvidence#FIELD}.
 */
final class DriverEvidenceRegistry {

    private static final Set<String> FIELD_VERIFIED = Set.of();

    private DriverEvidenceRegistry() {
    }

    static DriverEvidence resolve(String driverId) {
        if (driverId != null && FIELD_VERIFIED.contains(driverId)) {
            return DriverEvidence.FIELD;
        }
        return DriverEvidence.LOOPBACK_VERIFIED;
    }

    static Set<String> fieldVerifiedIds() {
        return FIELD_VERIFIED;
    }
}
