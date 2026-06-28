package com.ispf.driver.iec104;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.iec104server.Iec104ServerDeviceDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Iec104DeviceDriverTest {

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema FLOAT_SCHEMA = DataSchema.builder("floatValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private Iec104ServerDeviceDriver serverDriver;

    @AfterEach
    void tearDown() {
        if (serverDriver != null) {
            serverDriver.disconnect();
            serverDriver = null;
        }
    }

    @Test
    void writeSingleCommandUpdatesServerState() throws Exception {
        int port = freePort();
        StubDriverObject serverObject = new StubDriverObject(Map.of(
                "listenPort", String.valueOf(port),
                "commonAddress", "1"
        ));
        serverDriver = new Iec104ServerDeviceDriver();
        serverDriver.initialize(serverObject);
        serverDriver.connect();
        awaitPortOpen(port);
        serverDriver.readPoints(Map.of("relay", "2001"));

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "commonAddress", "1",
                "timeoutMs", "10000"
        ));
        Iec104DeviceDriver client = new Iec104DeviceDriver();
        client.initialize(clientObject);
        client.connect();
        client.readPoints(Map.of("relay", "2001:BOOL"));

        client.writePoint("relay", DataRecord.single(BOOL_SCHEMA, Map.of("value", true)));

        awaitServerValue(serverObject, "relay", 1.0);
        client.disconnect();
    }

    @Test
    void writeShortFloatUpdatesServerState() throws Exception {
        int port = freePort();
        StubDriverObject serverObject = new StubDriverObject(Map.of(
                "listenPort", String.valueOf(port),
                "commonAddress", "1"
        ));
        serverDriver = new Iec104ServerDeviceDriver();
        serverDriver.initialize(serverObject);
        serverDriver.connect();
        awaitPortOpen(port);
        serverDriver.readPoints(Map.of("setpoint", "3001"));

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "commonAddress", "1",
                "timeoutMs", "10000"
        ));
        Iec104DeviceDriver client = new Iec104DeviceDriver();
        client.initialize(clientObject);
        client.connect();
        client.readPoints(Map.of("setpoint", "3001:FLOAT"));

        client.writePoint("setpoint", DataRecord.single(FLOAT_SCHEMA, Map.of("value", 42.5)));

        awaitServerValue(serverObject, "setpoint", 42.5);
        client.disconnect();
    }

    @Test
    void rejectsUnknownPoint() throws Exception {
        int port = freePort();
        startServer(port);

        StubDriverObject clientObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "commonAddress", "1",
                "timeoutMs", "10000"
        ));
        Iec104DeviceDriver client = new Iec104DeviceDriver();
        client.initialize(clientObject);
        client.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                client.writePoint("missing", DataRecord.single(BOOL_SCHEMA, Map.of("value", true))));
        assertTrue(error.getMessage().contains("Unknown point"));
        client.disconnect();
    }

    private void startServer(int port) throws Exception {
        StubDriverObject serverObject = new StubDriverObject(Map.of(
                "listenPort", String.valueOf(port),
                "commonAddress", "1"
        ));
        serverDriver = new Iec104ServerDeviceDriver();
        serverDriver.initialize(serverObject);
        serverDriver.connect();
        awaitPortOpen(port);
    }

    private static void awaitPortOpen(int port) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException ignored) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
        throw new IllegalStateException("IEC104 server did not open port " + port);
    }

    private static void awaitServerValue(StubDriverObject serverObject, String pointId, double expected)
            throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            DataRecord record = serverObject.variables.get(pointId);
            if (record != null) {
                Object value = record.firstRow().get("value");
                if (value instanceof Number number && Math.abs(number.doubleValue() - expected) < 0.01) {
                    return;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        DataRecord record = serverObject.variables.get(pointId);
        assertEquals(expected, ((Number) record.firstRow().get("value")).doubleValue(), 0.01);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

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
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
            // no-op
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
