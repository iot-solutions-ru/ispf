package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualDeviceDriverTest {

    @Test
    void outOfBoxPollWritesWavesAndCoreTypes() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = stub(updates, Map.of(
                "sineAmplitude", "10.0",
                "sawtoothAmplitude", "5.0",
                "periodSec", "10"
        ));

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());

        assertTrue(updates.containsKey("sineWave"));
        assertTrue(updates.containsKey("sawtoothWave"));
        assertTrue(updates.containsKey("triangleWave"));
        assertTrue(updates.containsKey("temperature"));
        assertTrue(updates.containsKey("status"));
        assertTrue(updates.containsKey("meterLiters"));
        assertTrue(updates.containsKey("telemetryTable"));

        double sine = ((Number) updates.get("sineWave").firstRow().get("value")).doubleValue();
        double sawtooth = ((Number) updates.get("sawtoothWave").firstRow().get("value")).doubleValue();
        assertTrue(sine >= -10.0 && sine <= 10.0);
        assertTrue(sawtooth >= -5.0 && sawtooth <= 5.0);
    }

    @Test
    void sineAmplitudeFallsBackFromAmplitude() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = stub(updates, Map.of(
                "amplitude", "7.0",
                "sineAmplitude", "0",
                "periodSec", "10"
        ));

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());

        double sine = ((Number) updates.get("sineWave").firstRow().get("value")).doubleValue();
        assertTrue(sine >= -7.0 && sine <= 7.0);
    }

    private static DeviceDriver.DriverObject stub(Map<String, DataRecord> updates, Map<String, String> config) {
        return new DeviceDriver.DriverObject() {
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
                return Optional.ofNullable(updates.get(name));
            }

            @Override
            public void log(DeviceDriver.DriverLogLevel level, String message) {
            }

            @Override
            public Map<String, String> configuration() {
                return config;
            }
        };
    }
}
