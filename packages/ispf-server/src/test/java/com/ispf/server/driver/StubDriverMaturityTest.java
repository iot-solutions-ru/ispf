package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StubDriverMaturityTest {

    /** BL-191: shells / incomplete stacks stay BETA; cwmp remains PRODUCTION. */
    @Test
    void cwmpRemainsProduction() {
        assertEquals(DriverMaturity.PRODUCTION, DriverMaturityRegistry.resolve("cwmp"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "opc-da",
            "opc-bridge"
    })
    void shellIndustrialDriversAreBeta(String driverId) {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve(driverId), driverId);
    }

    @Test
    void corbaRemainsBetaWithoutOrb() {
        assertEquals(DriverMaturity.BETA, DriverMaturityRegistry.resolve("corba"));
    }

    /** Batch C (July 2026): real protocol paths replaced the former shells. */
    @ParameterizedTest
    @ValueSource(strings = {
            "ethernet-ip",
            "vmware",
            "smi-s"
    })
    void batchCDriversAreProduction(String driverId) {
        assertEquals(DriverMaturity.PRODUCTION, DriverMaturityRegistry.resolve(driverId), driverId);
    }
}
