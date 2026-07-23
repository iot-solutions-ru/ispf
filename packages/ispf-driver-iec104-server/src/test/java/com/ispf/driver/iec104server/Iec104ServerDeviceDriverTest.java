package com.ispf.driver.iec104server;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.ASduType;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.ClientConnectionBuilder;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.ie.IeQualifierOfSetPointCommand;
import org.openmuc.j60870.ie.IeQuality;
import org.openmuc.j60870.ie.IeShortFloat;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.InformationObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
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
 * In-module loopback tests for {@link Iec104ServerDeviceDriver}: a real j60870 client connects to the
 * driver over TCP and pushes ASDUs; the assertions observe the driver's variable updates.
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
    private Connection clientConnection;

    @AfterEach
    void tearDown() {
        if (clientConnection != null) {
            clientConnection.close();
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

        Connection client = newClient(port);
        client.startDataTransfer();
        client.singleCommand(COMMON_ADDRESS, CauseOfTransmission.ACTIVATION, 2001,
                new IeSingleCommand(true, 0, false));

        awaitValue(object, "relay", 1.0);
        DataRecord on = object.variables.get("relay");
        assertEquals(true, on.firstRow().get("clientConnected"));
        assertEquals("GOOD", on.firstRow().get("quality"));
        // Note: j60870 server-side connections never learn the client's originator address from
        // received ASDUs — Connection.getOriginatorAddress() returns the locally configured default (0).
        assertEquals(0, number(on, "clientOriginatorAddress"), 0.001);

        client.singleCommand(COMMON_ADDRESS, CauseOfTransmission.ACTIVATION, 2001,
                new IeSingleCommand(false, 0, false));
        awaitValue(object, "relay", 0.0);
    }

    @Test
    void shortFloatSetpointUpdatesIoaState() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("setpoint", "3001"));

        Connection client = newClient(port);
        client.startDataTransfer();
        client.setShortFloatCommand(COMMON_ADDRESS, CauseOfTransmission.ACTIVATION, 3001,
                new IeShortFloat(42.5f), new IeQualifierOfSetPointCommand(0, false));

        awaitValue(object, "setpoint", 42.5);
    }

    @Test
    void measuredShortFloatUpdatesIoaState() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("temperature", "4001"));

        Connection client = newClient(port);
        client.startDataTransfer();
        ASdu measurement = new ASdu(ASduType.M_ME_NC_1, false, CauseOfTransmission.SPONTANEOUS, false, false,
                0, COMMON_ADDRESS,
                new InformationObject(4001, new IeShortFloat(13.25f), new IeQuality(false, false, false, false, false)));
        client.send(measurement);

        awaitValue(object, "temperature", 13.25);
        assertEquals("GOOD", object.variables.get("temperature").firstRow().get("quality"));
    }

    @Test
    void asduWithForeignCommonAddressIsIgnored() throws Exception {
        int port = freePort();
        StubDriverObject object = startServer(port);
        driver.readPoints(Map.of("relay", "2001"));

        Connection client = newClient(port);
        client.startDataTransfer();
        client.singleCommand(COMMON_ADDRESS + 1, CauseOfTransmission.ACTIVATION, 2001,
                new IeSingleCommand(true, 0, false));

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
        // j60870 Server.start() binds synchronously, so the port is ready here. Note: any accepted
        // TCP connection flips the driver's clientConnected flag — do not probe the port with a
        // bare socket in these tests.
        return object;
    }

    private Connection newClient(int port) throws IOException {
        clientConnection = new ClientConnectionBuilder(InetAddress.getByName("127.0.0.1"))
                .setPort(port)
                .setConnectionTimeout(5000)
                .setConnectionEventListener(new ConnectionEventListener() {
                    @Override
                    public void newASdu(Connection connection, ASdu aSdu) {
                        // no server-initiated ASDUs expected in these tests
                    }

                    @Override
                    public void connectionClosed(Connection connection, IOException cause) {
                        // no-op
                    }

                    @Override
                    public void dataTransferStateChanged(Connection connection, boolean stopped) {
                        // no-op
                    }
                })
                .build();
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
