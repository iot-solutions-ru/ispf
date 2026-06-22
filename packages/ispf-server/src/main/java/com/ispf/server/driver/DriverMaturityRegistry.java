package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;

import java.util.Map;

/**
 * Central catalog of driver production readiness labels (see docs/DRIVERS.md).
 */
final class DriverMaturityRegistry {

    private static final Map<String, DriverMaturity> MATURITIES = Map.ofEntries(
            Map.entry("dnp3", DriverMaturity.BETA),
            Map.entry("ethernet-ip", DriverMaturity.BETA),
            Map.entry("opc-da", DriverMaturity.BETA),
            Map.entry("opc-bridge", DriverMaturity.BETA),
            Map.entry("corba", DriverMaturity.BETA),
            Map.entry("vmware", DriverMaturity.BETA),
            Map.entry("smi-s", DriverMaturity.BETA),
            Map.entry("cwmp", DriverMaturity.PRODUCTION),
            Map.entry("dlms", DriverMaturity.BETA),
            Map.entry("wmi", DriverMaturity.BETA),
            Map.entry("odbc", DriverMaturity.BETA),
            Map.entry("graph-db", DriverMaturity.BETA),
            Map.entry("flexible", DriverMaturity.PRODUCTION),
            Map.entry("gps-tracker", DriverMaturity.PRODUCTION)
    );

    private DriverMaturityRegistry() {
    }

    static DriverMaturity resolve(String driverId) {
        return MATURITIES.getOrDefault(driverId, DriverMaturity.PRODUCTION);
    }
}
