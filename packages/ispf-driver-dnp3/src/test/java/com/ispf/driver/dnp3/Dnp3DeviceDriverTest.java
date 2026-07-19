package com.ispf.driver.dnp3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Dnp3DeviceDriverTest {

    private static final int MASTER_ADDRESS = 1;
    private static final int OUTSTATION_ADDRESS = 1024;

    private Dnp3LoopbackOutstation outstation;

    @AfterEach
    void tearDown() {
        if (outstation != null) {
            outstation.close();
            outstation = null;
        }
    }

    @Test
    void readsAnalogInputViaClassPoll() throws Exception {
        outstation = new Dnp3LoopbackOutstation(MASTER_ADDRESS, OUTSTATION_ADDRESS);
        outstation.awaitBound(5_000);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(outstation.port()),
                "localAddress", String.valueOf(MASTER_ADDRESS),
                "outstationAddress", String.valueOf(OUTSTATION_ADDRESS),
                "timeoutMs", "10000"
        ));
        Dnp3DeviceDriver driver = new Dnp3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temp", "0:ANALOG_INPUT"));

        DataRecord record = driverObject.variables.get("temp");
        assertEquals(12.34, ((Number) record.firstRow().get("value")).doubleValue(), 0.001);
        assertEquals("0x01", record.firstRow().get("status"));
        driver.disconnect();
    }

    @Test
    void readsBinaryInputAndCounter() throws Exception {
        outstation = new Dnp3LoopbackOutstation(MASTER_ADDRESS, OUTSTATION_ADDRESS);
        outstation.awaitBound(5_000);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(outstation.port()),
                "localAddress", String.valueOf(MASTER_ADDRESS),
                "outstationAddress", String.valueOf(OUTSTATION_ADDRESS),
                "timeoutMs", "10000"
        ));
        Dnp3DeviceDriver driver = new Dnp3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "flag", "0:BINARY_INPUT",
                "count", "0:COUNTER"
        ));

        assertEquals(true, driverObject.variables.get("flag").firstRow().get("value"));
        assertEquals(999L, driverObject.variables.get("count").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writePointNotImplemented() {
        Dnp3DeviceDriver driver = new Dnp3DeviceDriver();
        DriverException ex = assertThrows(
                DriverException.class,
                () -> driver.writePoint("0:ANALOG_OUTPUT", DataRecord.single(
                        DataSchema.builder("dnp3Value").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 1.0)
                ))
        );
        assertTrue(ex.getMessage().toLowerCase().contains("not implemented"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
