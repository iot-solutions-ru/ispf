package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StubDriverMaturityTest {

    /** BL-140: promoted to PRODUCTION (Phase 25 OT Trust). */
    @ParameterizedTest
    @ValueSource(strings = {
            "ethernet-ip",
            "opc-da",
            "opc-bridge"
    })
    void promotedIndustrialDriversAreProduction(String driverId) {
        assertEquals(DriverMaturity.PRODUCTION, DriverMaturityRegistry.resolve(driverId), driverId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "corba",
            "vmware",
            "smi-s"
    })
    void remainingStubDriversAreBeta(String driverId) {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve(driverId), driverId);
    }

    @Test
    void bl26StubDriversStillBetaInRegistry() {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("corba"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("vmware"));
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("smi-s"));
    }
}
