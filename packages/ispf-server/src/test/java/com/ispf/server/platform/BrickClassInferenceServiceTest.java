package com.ispf.server.platform;

import com.ispf.server.driver.DriverPointMappingParser.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrickClassInferenceServiceTest {

    @Test
    void infersTemperatureSensorFromTempTag() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("point", "sensor", "temp"),
                "point",
                null,
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Temperature_Sensor", result.brickClass());
        assertEquals("brick:Temperature_Sensor", result.brickClassCompact());
        assertEquals(BrickClassInferenceService.CONFIDENCE_HIGH, result.confidence());
        assertEquals("point", result.entityKind());
        assertTrue(result.reason().contains("temp"));
    }

    @Test
    void infersAirHandlerUnitFromAhuTag() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("equip", "ahu", "air"),
                "equip",
                "North AHU",
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Air_Handler_Unit", result.brickClass());
        assertEquals("brick:Air_Handler_Unit", result.brickClassCompact());
        assertEquals(BrickClassInferenceService.CONFIDENCE_HIGH, result.confidence());
    }

    @Test
    void infersMeterFromMeterTag() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("equip", "meter", "elec"),
                "equip",
                "Main meter",
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Meter", result.brickClass());
        assertEquals("brick:Meter", result.brickClassCompact());
    }

    @Test
    void infersSensorForTempSensorEquipTags() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("equip", "sensor", "temp"),
                "equip",
                "Supply temp sensor",
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Sensor", result.brickClass());
        assertEquals("brick:Sensor", result.brickClassCompact());
    }

    @Test
    void usesPointMappingTagsForEquipWhenEquipTagsAreGeneric() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("equip", "lab"),
                "equip",
                "Lab device",
                List.of("point", "sensor", "temp")
        );
        assertEquals(BrickExportService.BRICK_NS + "Temperature_Sensor", result.brickClass());
        assertEquals(BrickClassInferenceService.CONFIDENCE_MEDIUM, result.confidence());
        assertTrue(result.reason().contains("driver point mapping tags"));
    }

    @Test
    void fallsBackToDefaultSensorForUnknownPointTags() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("point", "sensor"),
                "point",
                null,
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Sensor", result.brickClass());
        assertEquals(BrickClassInferenceService.CONFIDENCE_LOW, result.confidence());
    }

    @Test
    void fallsBackToDefaultEquipmentForUnknownEquipTags() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("equip", "site"),
                "equip",
                "Unknown equip",
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Equipment", result.brickClass());
        assertEquals(BrickClassInferenceService.CONFIDENCE_LOW, result.confidence());
    }

    @Test
    void infersEnergySensorFromPowerTag() {
        BrickClassInferenceService.InferenceResult result = BrickClassInferenceService.infer(
                List.of("point", "sensor", "power", "elec"),
                "point",
                null,
                List.of()
        );
        assertEquals(BrickExportService.BRICK_NS + "Energy_Sensor", result.brickClass());
    }

    @Test
    void resolvePointBrickClassMatchesExportServiceBehavior() {
        Entry tempMapping = new Entry("sim", List.of("point", "sensor", "temp"), "°C", "Temp");
        Entry genericMapping = new Entry("sim", List.of("point", "sensor"), "", "Status");

        assertEquals(
                BrickExportService.BRICK_NS + "Temperature_Sensor",
                BrickClassInferenceService.resolvePointBrickClass(tempMapping)
        );
        assertEquals(
                BrickExportService.BRICK_NS + "Sensor",
                BrickClassInferenceService.resolvePointBrickClass(genericMapping)
        );
    }

    @Test
    void normalizeTagsSplitsCommaSeparatedValues() {
        assertEquals(
                List.of("equip", "meter"),
                BrickClassInferenceService.normalizeTags(List.of("equip,meter"))
        );
    }
}
