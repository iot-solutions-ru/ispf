package com.ispf.driver;

/**
 * Production readiness of a device driver plugin.
 */
public enum DriverMaturity {
    /** Fully supported for typical deployments. */
    PRODUCTION,
    /** Functional but limited scope, platform constraints, or incomplete protocol stack. */
    BETA,
    /** Connectivity or placeholder implementation; not suitable for production telemetry. */
    STUB
}
