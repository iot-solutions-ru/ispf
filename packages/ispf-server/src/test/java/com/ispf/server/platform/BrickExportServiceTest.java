package com.ispf.server.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrickExportServiceTest {

    @Test
    void entityIriEncodesObjectPath() {
        assertEquals(
                "urn:ispf:platform:platform/devices/lab-userA-01",
                BrickExportService.entityIri("root.platform.devices.lab-userA-01")
        );
        assertEquals(
                "urn:ispf:platform:platform/devices/lab-userA-01/sineWave",
                BrickExportService.entityIri("root.platform.devices.lab-userA-01", "sineWave")
        );
    }

    @Test
    void resolvesBrickClassCurieAndFragment() {
        assertEquals(
                BrickExportService.BRICK_NS + "Sensor",
                BrickExportService.resolveBrickClass("brick:Sensor")
        );
        assertEquals(
                "https://example.org/custom#Equip",
                BrickExportService.resolveBrickClass("https://example.org/custom#Equip")
        );
    }

    @Test
    void compactTypeUsesBrickPrefix() {
        assertEquals("brick:Sensor", BrickExportService.compactType(BrickExportService.BRICK_NS + "Sensor"));
    }
}
