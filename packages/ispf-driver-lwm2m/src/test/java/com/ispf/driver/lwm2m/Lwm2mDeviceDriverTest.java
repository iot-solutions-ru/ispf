package com.ispf.driver.lwm2m;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link Lwm2mDeviceDriver} against an in-process fake CoAP/LwM2M resource server.
 */
class Lwm2mDeviceDriverTest {

    private Lwm2mDeviceDriver driver;
    private FakeLwm2mCoapServer server;

    @AfterEach
    void tearDown() {
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
    void registerProbeAndResourceRead() throws Exception {
        server = new FakeLwm2mCoapServer();
        server.put("/rd", "ep=lab-device");
        server.put("/1/0/0", "ISPF");
        server.put("/3/0/0", "LabManufacturer");
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000",
                "probeRegisterOnConnect", "true",
                "registerPath", "/rd"
        ));
        driver = new Lwm2mDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "manufacturer", "/1/0/0",
                "device", "3/0/0"
        ));
        assertEquals("ISPF", object.variables.get("manufacturer").firstRow().get("value"));
        assertEquals("/1/0/0", object.variables.get("manufacturer").firstRow().get("path"));
        assertEquals("LabManufacturer", object.variables.get("device").firstRow().get("value"));
        assertEquals("/3/0/0", object.variables.get("device").firstRow().get("path"));
    }

    @Test
    void writeIsReadOnly() throws Exception {
        server = new FakeLwm2mCoapServer();
        server.put("/rd", "ok");
        server.start();

        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "probeRegisterOnConnect", "true"
        )));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("x", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("probeRegisterOnConnect", "false")));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("m", "/1/0/0")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsWhenServerSilent() throws Exception {
        int closedPort;
        try (ServerSocket tcp = new ServerSocket(0)) {
            closedPort = tcp.getLocalPort();
        }
        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200",
                "probeRegisterOnConnect", "true"
        )));
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("LwM2M connect probe failed"));
    }

    @Test
    void pathNormalization() {
        assertEquals("/1/0/0", CoapGetClient.normalizePath("1/0/0"));
        assertEquals("/rd", CoapGetClient.normalizePath("/rd"));
        assertEquals(3, CoapGetClient.pathSegments("/1/0/0").size());
    }

    private static final class FakeLwm2mCoapServer implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-lwm2m-coap");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> resources = new ConcurrentHashMap<>();
        private volatile boolean running = true;

        FakeLwm2mCoapServer() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(String path, String value) {
            resources.put(CoapGetClient.normalizePath(path), value);
        }

        void start() {
            executor.submit(this::loop);
        }

        private void loop() {
            byte[] buf = new byte[1024];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    CoapGetClient.ParsedRequest request = CoapGetClient.parseRequest(data);
                    if (request.code() != CoapGetClient.CODE_GET) {
                        continue;
                    }
                    String payload = resources.getOrDefault(request.path(), "");
                    byte[] response = CoapGetClient.buildContentAck(request.messageId(), request.token(), payload);
                    socket.send(new DatagramPacket(
                            response, response.length, packet.getAddress(), packet.getPort()));
                } catch (IOException e) {
                    if (!running || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        @Override
        public void close() {
            running = false;
            socket.close();
            executor.shutdownNow();
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
                    "test-lwm2m",
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
