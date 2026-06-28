package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlmsDeviceDriverTest {

    private static final int CLIENT_ADDRESS = 16;
    private static final int LOGICAL_DEVICE = 1;

    private static final DataSchema DOUBLE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private DlmsLoopbackServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.closeServer();
            server = null;
        }
    }

    @Test
    void readsRegisterViaAssociation() throws Exception {
        server = new DlmsLoopbackServer(CLIENT_ADDRESS);
        StubDriverObject driverObject = driverConfig(server.port());
        DlmsDeviceDriver driver = new DlmsDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("energy", "1:" + DlmsLoopbackServer.ENERGY_OBIS));

        DataRecord record = driverObject.variables.get("energy");
        assertEquals("42.0", record.firstRow().get("value"));
        assertEquals("GOOD", record.firstRow().get("quality"));
        driver.disconnect();
    }

    @Test
    void writeRegisterUpdatesServerState() throws Exception {
        server = new DlmsLoopbackServer(CLIENT_ADDRESS);
        StubDriverObject driverObject = driverConfig(server.port());
        DlmsDeviceDriver driver = new DlmsDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("energy", "1:" + DlmsLoopbackServer.ENERGY_OBIS));

        driver.writePoint("energy", DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 77.5)));

        assertEquals(77.5, server.energyValue(), 0.001);
        assertEquals("77.5", driverObject.variables.get("energy").firstRow().get("value"));
        driver.disconnect();
    }

    private static StubDriverObject driverConfig(int port) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "clientAddress", String.valueOf(CLIENT_ADDRESS),
                "logicalDevice", String.valueOf(LOGICAL_DEVICE),
                "timeoutMs", "15000"
        ));
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
                    "test-meter",
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
