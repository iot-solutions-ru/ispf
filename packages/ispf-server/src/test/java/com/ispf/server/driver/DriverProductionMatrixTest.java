package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverProductionMatrixTest {

    @Test
    void top10IndustrialDriversAreProduction() {
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            assertEquals(
                    DriverMaturity.PRODUCTION,
                    DriverProductionMatrix.resolveMaturity(driverId),
                    driverId
            );
            DriverProductionMatrix.Entry entry = DriverProductionMatrix.entry(driverId).orElseThrow();
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    driverId + " missing loopback test"
            );
        }
    }

    @Test
    void top20IndustrialDriversAreProductionOrInteropPartner() {
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            if ("iec104-server".equals(driverId)) {
                continue;
            }
            assertEquals(
                    DriverMaturity.PRODUCTION,
                    DriverProductionMatrix.resolveMaturity(driverId),
                    driverId
            );
            DriverProductionMatrix.Entry entry = DriverProductionMatrix.entry(driverId).orElseThrow();
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    driverId + " missing loopback test"
            );
        }
    }

    @Test
    void productionDriversDeclareLoopbackTestSource() {
        for (DriverProductionMatrix.Entry entry : DriverProductionMatrix.entries().values()) {
            if (entry.maturity() != DriverMaturity.PRODUCTION) {
                continue;
            }
            assertNotNull(entry.loopbackTestSourcePath(), entry.driverId() + " PRODUCTION requires loopback test");
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    entry.driverId() + " missing loopback test source: " + entry.loopbackTestSourcePath()
            );
        }
    }

    @Test
    void maturityRegistryMatchesMatrix() {
        for (String driverId : DriverProductionMatrix.entries().keySet()) {
            assertEquals(
                    DriverProductionMatrix.resolveMaturity(driverId),
                    DriverMaturityRegistry.resolve(driverId),
                    driverId
            );
        }
    }

    @Test
    void topProtocolsDeclareObservedAtCapability() {
        for (String driverId : new String[] { "modbus-tcp", "opcua", "bacnet", "s7", "snmp" }) {
            assertTrue(
                    DriverProductionMatrix.resolveCapabilities(driverId).contains(DriverProductionMatrix.Capability.OBSERVED_AT),
                    driverId
            );
        }
    }

    @Test
    void top10IndustrialDriversLinkedToInteropMatrix() {
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId
            );
        }
    }

    @Test
    void top20IndustrialDriversLinkedToInteropMatrix() {
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId
            );
        }
    }
}
