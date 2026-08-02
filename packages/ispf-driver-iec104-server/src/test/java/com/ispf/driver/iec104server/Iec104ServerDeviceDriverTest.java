package com.ispf.driver.iec104server;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.iec104.codec.Iec104Asdu;
import com.ispf.driver.iec104.codec.Iec104Cause;
import com.ispf.driver.iec104.codec.Iec104Connection;
import com.ispf.driver.iec104.codec.Iec104ConnectionListener;
import com.ispf.driver.iec104.codec.Iec104Type;
import com.ispf.driver.iec104.codec.Iec104Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-module loopback tests for {@link Iec104ServerDeviceDriver}: the ISPF-owned IEC104 client codec
 * connects to the driver over TCP and pushes ASDUs; assertions observe the driver's variable updates.
 */
class Iec104ServerDeviceDriverTest {

    private static final int COMMON_ADDRESS = 1;

    private static final DataSchema FLOAT_SCHEMA = DataSchema.builder("floatValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private Iec104ServerDeviceDriver driver;
    private Iec104Connection clientConnection;

    @AfterEach
    void tearDown() {
        if (clientConnection != null) {
            try {
                clientConnection.close();
            } catch (IOException ignored) {
                // best effort
            }
            clientConnection = null;
        }
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
    }

    @Test
    void readPointsBeforeConnectThrows() throws Exception {
        StubDriverObject object = new StubDriverObject(Map.of());
        driver = new Iec104ServerDeviceDriver();
        driver.initialize(object);

        assertFalse(driver.isConnected());
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("relay", "2001")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void singleCommandUpdatesIoaStateAndConnectionInfo() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("relay", "2001"));

        // Before any client connects the point is exposed with NOT_CONNECTED quality.
        DataRecord initial = object.variables.get("relay");
        assertNotNull(initial);
        assertEquals(0.0, number(initial, "value"), 0.001);
        assertEquals(false, initial.firstRow().get("clientConnected"));
        assertEquals("NOT_CONNECTED", initial.firstRow().get("quality"));
        assertEquals(-1, number(initial, "clientOriginatorAddress"), 0.001);

        Iec104Connection client = newClient(port);
        client.startDataTransfer();
        client.singleCommand(COMMON_ADDRESS, 2001, true);

        awaitValue(object, "relay", 1.0);
        DataRecord on = object.variables.get("relay");
        assertEquals(true, on.firstRow().get("clientConnected"));
        assertEquals("GOOD", on.firstRow().get("quality"));
        assertEquals(0, number(on, "clientOriginatorAddress"), 0.001);

        client.singleCommand(COMMON_ADDRESS, 2001, false);
        awaitValue(object, "relay", 0.0);
    }

    @Test
    void shortFloatSetpointUpdatesIoaState() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("setpoint", "3001"));

        Iec104Connection client = newClient(port);
        client.startDataTransfer();
        client.setShortFloatCommand(COMMON_ADDRESS, 3001, 42.5);

        awaitValue(object, "setpoint", 42.5);
    }

    @Test
    void measuredShortFloatUpdatesIoaState() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("temperature", "4001"));

        Iec104Connection client = newClient(port);
        client.startDataTransfer();
        Iec104Asdu measurement = new Iec104Asdu(Iec104Type.M_ME_NC_1, Iec104Cause.SPONTANEOUS, 0,
                COMMON_ADDRESS, List.of(Iec104Value.shortFloat(4001, 13.25, "GOOD")));
        client.sendAsdu(measurement);

        awaitValue(object, "temperature", 13.25);
        assertEquals("GOOD", object.variables.get("temperature").firstRow().get("quality"));
    }

    @Test
    void asduWithForeignCommonAddressIsIgnored() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("relay", "2001"));

        Iec104Connection client = newClient(port);
        client.startDataTransfer();
        client.singleCommand(COMMON_ADDRESS + 1, 2001, true);

        TimeUnit.MILLISECONDS.sleep(700);
        assertEquals(0.0, number(object.variables.get("relay"), "value"), 0.001);
    }

    @Test
    void writePointMutatesIoaState() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("relay", "2001"));

        driver.writePoint("relay", DataRecord.single(FLOAT_SCHEMA, Map.of("value", 7.25)));

        DataRecord record = object.variables.get("relay");
        assertEquals(7.25, number(record, "value"), 0.001);
    }

    @Test
    void writePointWithUnknownPointThrows() throws Exception {
        int port = freePort();
        startServer(port);

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(FLOAT_SCHEMA, Map.of("value", 1.0))));
        assertTrue(error.getMessage().contains("Unknown point"));
    }

    private StubDriverObject startServer(int port) throws Exception {
        StubDriverObject object = new StubDriverObject(Map.of(
                "listenPort", String.valueOf(port),
                "commonAddress", String.valueOf(COMMON_ADDRESS)
        ));
        driver = new Iec104ServerDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        // Server.start() binds synchronously, so the port is ready here. Note: any accepted
        // TCP connection flips the driver's clientConnected flag — do not probe the port with a
        // bare socket in these tests.
        return object;
    }

    private Iec104Connection newClient(int port) throws IOException {
        clientConnection = Iec104Connection.connect(InetAddress.getByName("127.0.0.1"), port, 5000,
                new Iec104ConnectionListener() {
                    @Override
                    public void onAsdu(Iec104Connection connection, Iec104Asdu asdu) {
                        // no server-initiated ASDUs expected in these tests
                    }
                });
        return clientConnection;
    }

    private static void awaitValue(StubDriverObject object, String pointId, double expected)
            throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            DataRecord record = object.variables.get(pointId);
            if (record != null && Math.abs(number(record, "value") - expected) < 0.01) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        DataRecord record = object.variables.get(pointId);
        assertNotNull(record, "No variable update received for point " + pointId);
        assertEquals(expected, number(record, "value"), 0.01);
    }

    private static double number(DataRecord record, String field) {
        return ((Number) record.firstRow().get(field)).doubleValue();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-iec104-server",
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
