package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void protocolStubCatalogIsLoaded() {
        assertTrue(
                DriverProductionMatrix.protocolStubIds().size() > 40,
                "protocol stub catalog should be generated"
        );
        assertFalse(DriverProductionMatrix.protocolStubIds().contains("opcua"));
        assertFalse(DriverProductionMatrix.protocolStubIds().contains("modbus-tcp"));
    }

    @ParameterizedTest
    @MethodSource("protocolStubIds")
    void protocolCatalogStubsAreStubMaturity(String driverId) {
        assertEquals(DriverMaturity.STUB, DriverMaturityRegistry.resolve(driverId), driverId);
    }

    static Stream<String> protocolStubIds() {
        return DriverProductionMatrix.protocolStubIds().stream().sorted();
    }
}
