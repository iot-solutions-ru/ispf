package com.ispf.driver;

/**
 * Production readiness of a device driver plugin.
 *
 * <p>{@link #PRODUCTION} is the lab CI bar ([ADR-0022](https://github.com/iot-solutions-ru/ispf/blob/main/docs/en/decisions/0022-driver-production-matrix.md)).
 * {@link #FIELD} and {@link #CERTIFIED} are evidence tiers above it ([ADR-0067](https://github.com/iot-solutions-ru/ispf/blob/main/docs/en/decisions/0067-field-certified-maturity.md)).
 */
public enum DriverMaturity {
    /**
     * Protocol conformance / formal cert evidence on top of {@link #FIELD} (or an explicit waiver).
     * Requires a resolvable certified evidence path in {@code DriverProductionMatrix}.
     */
    CERTIFIED,
    /**
     * Named-site soak (≥7 days) + OT sign-off for claimed capabilities.
     * Requires a resolvable field evidence path in {@code DriverProductionMatrix}.
     */
    FIELD,
    /** Lab CI: loopback/integration test, docs, honest capabilities — typical deployments. */
    PRODUCTION,
    /** Functional but limited scope, platform constraints, or incomplete protocol stack. */
    BETA,
    /** Connectivity or placeholder implementation; not suitable for production telemetry. */
    STUB;

    /** Lab PRODUCTION bar or a higher evidence tier (FIELD / CERTIFIED). */
    public boolean isAtLeastProduction() {
        return this == PRODUCTION || this == FIELD || this == CERTIFIED;
    }
}
