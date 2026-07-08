package com.ispf.driver.opcuaserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcUaServerDeviceDriverTest {

    private OpcUaServerDeviceDriver driver;
    private StubDriverObject driverObject;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        driverObject = null;
    }

    @Test
    void connectReadWriteRoundTrip() throws Exception {
        int port = freePort();
        startDriver(port, OpcUaServerInterop.DEFAULT_NAMESPACE_INDEX);

        assertTrue(driver.isConnected());
        assertEquals(OpcUaServerInterop.endpointUrl(port), driver.endpointUrl());

        driver.readPoints(Map.of("temperature", "Temperature"));
        DataRecord read = driverObject.variables.get("temperature");
        assertEquals("", read.firstRow().get("value"));
        assertTrue(read.firstRow().get("quality").toString().contains("Good"));

        driver.writePoint("temperature", DataRecord.single(
                com.ispf.core.model.DataSchema.builder("value")
                        .field("value", com.ispf.core.model.FieldType.STRING)
                        .build(),
                Map.of("value", "42.5")
        ));
        assertEquals("42.5", driverObject.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void interopConstantsMatchDriverMetadata() {
        assertEquals(OpcUaServerInterop.APPLICATION_URI, "urn:ispf:driver:opcua-server");
        assertEquals("opc.tcp://localhost:4840/ispf", OpcUaServerInterop.endpointUrl(4840));
        assertEquals("Objects/IspfVariables/Pressure", OpcUaServerInterop.browsePath("Pressure"));
    }

    private void startDriver(int port, int namespace) throws DriverException {
        driverObject = new StubDriverObject(Map.of(
                "bindPort", String.valueOf(port),
                "namespace", String.valueOf(namespace),
                "timeoutMs", "10000"
        ));
        driver = new OpcUaServerDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        private StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-opcua-server",
                    "root.platform.devices.opcua-server-test",
                    ObjectType.DEVICE,
                    "OPC UA Server Test",
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
