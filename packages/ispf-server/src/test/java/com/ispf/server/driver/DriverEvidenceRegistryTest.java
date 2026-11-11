package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverEvidenceRegistryTest {

    @Test
    void noDriverIsFieldProven() {
        assertTrue(DriverEvidenceRegistry.fieldVerifiedIds().isEmpty());
        for (String driverId : DriverProductionMatrix.entries().keySet()) {
            assertEquals(DriverEvidence.LOOPBACK_VERIFIED, DriverEvidenceRegistry.resolve(driverId), driverId);
        }
    }

    @Test
    void readOnlyFollowsMissingWriteCapability() {
        DriverListing gps = DriverListing.of(new DriverMetadata(
                "gps-tracker", "GPS", "1", "tracker", "ISPF", Map.of(), DriverMaturity.PRODUCTION,
                DriverCapabilityRegistry.resolve("gps-tracker")
        ));
        DriverListing modbus = DriverListing.of(new DriverMetadata(
                "modbus-tcp", "Modbus", "1", "tcp", "ISPF", Map.of(), DriverMaturity.PRODUCTION,
                DriverCapabilityRegistry.resolve("modbus-tcp")
        ));
        assertTrue(gps.readOnly());
        assertEquals(DriverEvidence.LOOPBACK_VERIFIED, gps.evidence());
        assertFalse(modbus.readOnly());
        assertTrue(modbus.capabilities().contains("write"));
        assertEquals(Set.of(), DriverEvidenceRegistry.fieldVerifiedIds());
    }
}
