package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;

/**
 * Central catalog of driver production readiness labels (see docs/DRIVERS.md, ADR-0022).
 */
final class DriverMaturityRegistry {

    private DriverMaturityRegistry() {
    }

    static DriverMaturity resolve(String driverId) {
        return DriverProductionMatrix.resolveMaturity(driverId);
    }
}
