package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    /**
     * Wave 11 cleared the protocol-stub catalog (all packs are matrix lab codecs).
     * The JSON resource must still load; core drivers must never reappear as stubs.
     */
    @Test
    void protocolStubCatalogIsLoaded() {
        assertEquals(
                0,
                DriverProductionMatrix.protocolStubIds().size(),
                "protocol stub catalog should be empty after Wave 11 lab promotions"
        );
        assertFalse(DriverProductionMatrix.protocolStubIds().contains("opcua"));
        assertFalse(DriverProductionMatrix.protocolStubIds().contains("modbus-tcp"));
    }

    @Test
    void protocolCatalogStubsAreStubMaturity() {
        for (String driverId : DriverProductionMatrix.protocolStubIds()) {
            assertEquals(DriverMaturity.STUB, DriverMaturityRegistry.resolve(driverId), driverId);
        }
    }
}
