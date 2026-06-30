package com.ispf.driver.snmp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.snmp4j.smi.Integer32;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnmpDeviceDriverTest {

    private static final String TEST_OID = "1.3.6.1.4.1.99999.1.1.0";
    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("snmpWrite")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .build();

    private SnmpLoopbackAgent agent;

    @AfterEach
    void tearDown() throws Exception {
        if (agent != null) {
            agent.close();
            agent = null;
        }
    }

    @Test
    void readsAndWritesOidViaLoopbackAgent() throws Exception {
        int port = freePort();
        agent = new SnmpLoopbackAgent(port);
        agent.setValue(TEST_OID, new Integer32(12));

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "community", "public",
                "version", "2c",
                "timeoutMs", "3000",
                "retries", "1"
        ));
        SnmpDeviceDriver driver = new SnmpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("gauge", TEST_OID + ":INTEGER"));

        DataRecord read = driverObject.variables.get("gauge");
        assertEquals(12.0, read.firstRow().get("value"));

        driver.writePoint("gauge", DataRecord.single(VALUE_SCHEMA, Map.of("value", 44.0, "raw", "44")));
        DataRecord afterWrite = driverObject.variables.get("gauge");
        assertEquals(44.0, afterWrite.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeRequiresConnection() {
        SnmpDeviceDriver driver = new SnmpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("gauge", DataRecord.single(VALUE_SCHEMA, Map.of("value", 1.0, "raw", "1"))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

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
                DataSchema schema = DataSchema.builder("config")
                        .field("value", FieldType.STRING)
                        .field("raw", FieldType.STRING)
                        .build();
                return Optional.of(DataRecord.single(schema, Map.of("value", value, "raw", value)));
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
