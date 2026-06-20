package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;

import java.util.Map;

/**
 * Central catalog of driver production readiness labels (see docs/DRIVERS.md).
 */
final class DriverMaturityRegistry {

    private static final Map<String, DriverMaturity> MATURITIES = Map.ofEntries(
            Map.entry("dnp3", DriverMaturity.STUB),
            Map.entry("ethernet-ip", DriverMaturity.STUB),
            Map.entry("opc-da", DriverMaturity.STUB),
            Map.entry("opc-bridge", DriverMaturity.STUB),
            Map.entry("corba", DriverMaturity.STUB),
            Map.entry("vmware", DriverMaturity.STUB),
            Map.entry("smi-s", DriverMaturity.STUB),
            Map.entry("cwmp", DriverMaturity.BETA),
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
