package com.ispf.driver.uds;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link UdsDeviceDriver} against an in-process DoIP/UDS lab server.
 * Certifies the lab subset (0x10/0x22/0x2E) only — not full ISO-TP / ISO 13400.
 */
class UdsDeviceDriverTest {

    private UdsDeviceDriver driver;
    private FakeDoipUdsServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataIsProductionReadWriteDoipLab() {
        driver = new UdsDeviceDriver();
        assertEquals("uds", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("doip"));
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("not"));
    }

    @Test
    void readDidMappings() throws Exception {
        server = new FakeDoipUdsServer();
        server.put(0xF190, "VIN123456789ABCDEF".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new UdsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertTrue(server.sessionActive());

        driver.readPoints(Map.of("vin", "0xF190"));
        assertEquals("VIN123456789ABCDEF", object.variables.get("vin").firstRow().get("value"));
        assertEquals("0xF190", object.variables.get("vin").firstRow().get("did"));

        driver.readPoints(Map.of("vin2", "DID:F190"));
        assertEquals("VIN123456789ABCDEF", object.variables.get("vin2").firstRow().get("value"));
        assertEquals(0xF190, UdsDeviceDriver.parseDidMapping("DID:F190"));
    }

    @Test
    void writeThenReadDid() throws Exception {
        server = new FakeDoipUdsServer();
        server.put(0xF190, "OLD".getBytes(StandardCharsets.US_ASCII));
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new UdsDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("vin", "0xF190"));
        driver.writePoint("vin", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "NEWVINVALUE0000001")
        ));
        assertEquals("NEWVINVALUE0000001",
                new String(server.get(0xF190), StandardCharsets.US_ASCII));

        driver.readPoints(Map.of("vin", "0xF190"));
        assertEquals("NEWVINVALUE0000001", object.variables.get("vin").firstRow().get("value"));
    }

    @Test
    void writeHexPayload() throws Exception {
        server = new FakeDoipUdsServer();
        server.put(0xF190, new byte[]{0x00});
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new UdsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("did", "0xF190"));
        driver.writePoint("did", DataRecord.single(
                DataSchema.builder("v").field("data", FieldType.STRING).build(),
                Map.of("data", "DEADBEEF")
        ));
        assertEquals("DEADBEEF", UdsDeviceDriver.toHex(server.get(0xF190)));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new UdsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "0xF190")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstUnreachableHost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200"
        ));
        driver = new UdsDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("UDS DoIP-lab connect failed"));
    }

    private static final class FakeDoipUdsServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-doip-uds");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, byte[]> dids = new ConcurrentHashMap<>();
        private volatile boolean sessionActive;

        FakeDoipUdsServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int did, byte[] data) {
            dids.put(did, data.clone());
        }

        byte[] get(int did) {
            return dids.get(did);
        }

        boolean sessionActive() {
            return sessionActive;
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    UdsDeviceDriver.DoipMessage message = UdsDeviceDriver.readDoipFrame(in);
                    if (message.payloadType() == UdsDeviceDriver.PAYLOAD_ROUTING_ACTIVATION_REQUEST) {
                        byte[] response = new byte[9];
                        if (message.payload().length >= 2) {
                            response[0] = message.payload()[0];
                            response[1] = message.payload()[1];
                        }
                        response[2] = 0x00;
                        response[3] = 0x01;
                        response[4] = 0x10; // routing successfully activated
                        UdsDeviceDriver.writeDoipFrame(
                                out, UdsDeviceDriver.PAYLOAD_ROUTING_ACTIVATION_RESPONSE, response);
                    } else if (message.payloadType() == UdsDeviceDriver.PAYLOAD_DIAGNOSTIC_MESSAGE) {
                        byte[] payload = message.payload();
                        if (payload.length < 5) {
                            continue;
                        }
                        byte[] uds = Arrays.copyOfRange(payload, 4, payload.length);
                        byte[] udsResponse = handleUds(uds);
                        byte[] reply = new byte[4 + udsResponse.length];
                        // swap source/target
                        reply[0] = payload[2];
                        reply[1] = payload[3];
                        reply[2] = payload[0];
                        reply[3] = payload[1];
                        System.arraycopy(udsResponse, 0, reply, 4, udsResponse.length);
                        UdsDeviceDriver.writeDoipFrame(
                                out, UdsDeviceDriver.PAYLOAD_DIAGNOSTIC_MESSAGE, reply);
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private byte[] handleUds(byte[] uds) {
            int sid = uds[0] & 0xFF;
            if (sid == UdsDeviceDriver.SID_DIAGNOSTIC_SESSION_CONTROL) {
                sessionActive = true;
                return new byte[]{(byte) 0x50, uds[1], 0x00, 0x32, 0x01, (byte) 0xF4};
            }
            if (sid == UdsDeviceDriver.SID_READ_DATA_BY_IDENTIFIER) {
                int did = ((uds[1] & 0xFF) << 8) | (uds[2] & 0xFF);
                byte[] data = dids.get(did);
                if (data == null) {
                    return new byte[]{0x7F, 0x22, 0x31};
                }
                byte[] response = new byte[3 + data.length];
                response[0] = 0x62;
                response[1] = uds[1];
                response[2] = uds[2];
                System.arraycopy(data, 0, response, 3, data.length);
                return response;
            }
            if (sid == UdsDeviceDriver.SID_WRITE_DATA_BY_IDENTIFIER) {
                int did = ((uds[1] & 0xFF) << 8) | (uds[2] & 0xFF);
                byte[] data = Arrays.copyOfRange(uds, 3, uds.length);
                dids.put(did, data);
                return new byte[]{0x6E, uds[1], uds[2]};
            }
            return new byte[]{0x7F, (byte) sid, 0x11};
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
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
                    "test-uds",
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
