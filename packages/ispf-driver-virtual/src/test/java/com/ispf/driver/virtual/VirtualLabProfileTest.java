package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualLabProfileTest {

    @Test
    void labProfileProducesSineAndSawtoothWaves() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = new DeviceDriver.DriverObject() {
            @Override
            public PlatformObject deviceObject() {
                return null;
            }

            @Override
            public void updateVariable(String name, DataRecord value) {
                updates.put(name, value);
            }

            @Override
            public Optional<DataRecord> getVariable(String name) {
                return Optional.empty();
            }

            @Override
            public void log(DeviceDriver.DriverLogLevel level, String message) {
            }

            @Override
            public Map<String, String> configuration() {
                return Map.of(
                        "profile", "lab",
                        "sineAmplitude", "10.0",
                        "sawtoothAmplitude", "5.0",
                        "periodSec", "10"
                );
            }
        };

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());

        assertTrue(updates.containsKey("sineWave"));
        assertTrue(updates.containsKey("sawtoothWave"));
        assertTrue(updates.containsKey("triangleWave"));
        assertTrue(updates.containsKey("status"));
        assertFalse(updates.containsKey("intValue"));
        assertFalse(updates.containsKey("floatValue"));

        double sine = ((Number) updates.get("sineWave").firstRow().get("value")).doubleValue();
        double sawtooth = ((Number) updates.get("sawtoothWave").firstRow().get("value")).doubleValue();
        assertTrue(sine >= -10.0 && sine <= 10.0);
        assertTrue(sawtooth >= -5.0 && sawtooth <= 5.0);
    }

    @Test
    void labProfileUsesSineAmplitudeFallbackFromAmplitude() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = new DeviceDriver.DriverObject() {
            @Override
            public PlatformObject deviceObject() {
                return null;
            }

            @Override
            public void updateVariable(String name, DataRecord value) {
                updates.put(name, value);
            }

            @Override
            public Optional<DataRecord> getVariable(String name) {
                return Optional.empty();
            }

            @Override
            public void log(DeviceDriver.DriverLogLevel level, String message) {
            }

            @Override
            public Map<String, String> configuration() {
                return Map.of(
                        "profile", "lab",
                        "amplitude", "8.0",
                        "sineAmplitude", "0",
                        "sawtoothAmplitude", "4.0",
                        "periodSec", "10"
                );
            }
        };

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());

        double sine = ((Number) updates.get("sineWave").firstRow().get("value")).doubleValue();
        assertTrue(sine >= -8.0 && sine <= 8.0);
    }
}
