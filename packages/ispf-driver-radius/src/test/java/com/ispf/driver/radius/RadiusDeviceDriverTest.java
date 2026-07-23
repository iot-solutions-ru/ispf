package com.ispf.driver.radius;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusServer;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link RadiusDeviceDriver}: a real in-process TinyRadius
 * {@link RadiusServer} answers the driver's Access-Request (PAP) probes.
 */
class RadiusDeviceDriverTest {

    private static final String SECRET = "testing123";

    private LoopbackRadiusServer server;
    private int port;
    private RadiusDeviceDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    void accessAcceptReportsSuccess() throws Exception {
        startServer();
        StubDriverObject driverObject = newDriver("alice", "wonderland", 1000);
        driver.connect();

        driver.readPoints(Map.of("radiusAuth", "auth"));

        DataRecord record = driverObject.variables.get("radiusAuth");
        assertEquals(true, record.firstRow().get("success"));
        assertEquals("success", record.firstRow().get("value"));
        assertEquals(RadiusPacket.ACCESS_ACCEPT, record.firstRow().get("responseCode"));
    }

    @Test
    void accessRejectReportsFailure() throws Exception {
        startServer();
        StubDriverObject driverObject = newDriver("alice", "wrong-password", 1000);
        driver.connect();

        driver.readPoints(Map.of("radiusAuth", "auth"));

        DataRecord record = driverObject.variables.get("radiusAuth");
        assertEquals(false, record.firstRow().get("success"));
        assertEquals("fail", record.firstRow().get("value"));
        assertEquals(RadiusPacket.ACCESS_REJECT, record.firstRow().get("responseCode"));
    }

    @Test
    void unreachableServerReportsFailureWithoutException() throws Exception {
        StubDriverObject driverObject = newDriver("alice", "wonderland", 300);
        driver.connect();

        assertDoesNotThrow(() -> driver.readPoints(Map.of("radiusAuth", "auth")));

        DataRecord record = driverObject.variables.get("radiusAuth");
        assertEquals(false, record.firstRow().get("success"));
        assertEquals("fail", record.firstRow().get("value"));
        assertEquals(-1, record.firstRow().get("responseCode"));
    }

    @Test
    void readPointsRequiresConnect() {
        driver = new RadiusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("radiusAuth", "auth")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeIsReadOnly() {
        driver = new RadiusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("radiusAuth", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject newDriver(String username, String password, int timeoutMs) throws Exception {
        if (server == null) {
            port = findFreeUdpPort();
        }
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "secret", SECRET,
                "username", username,
                "password", password,
                "timeoutMs", String.valueOf(timeoutMs)
        ));
        driver = new RadiusDeviceDriver();
        driver.initialize(driverObject);
        return driverObject;
    }

    private void startServer() throws Exception {
        port = findFreeUdpPort();
        server = new LoopbackRadiusServer();
        server.setListenAddress(InetAddress.getByName("127.0.0.1"));
        server.setAuthPort(port);
        server.start(true, false);
        assertTrue(server.awaitAuthSocketBound(5, TimeUnit.SECONDS), "RADIUS server socket did not bind");
    }

    private static int findFreeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    /**
     * TinyRadius binds the auth socket on a listener thread inside {@code start()};
     * this subclass signals once the socket exists so tests never race the bind.
     */
    private static final class LoopbackRadiusServer extends RadiusServer {

        private static final Map<String, String> USERS = Map.of("alice", "wonderland");
        private final CountDownLatch authSocketBound = new CountDownLatch(1);

        @Override
        public String getSharedSecret(InetSocketAddress client) {
            return SECRET;
        }

        @Override
        public String getUserPassword(String userName) {
            return USERS.get(userName);
        }

        @Override
        protected DatagramSocket getAuthSocket() throws SocketException {
            DatagramSocket socket = super.getAuthSocket();
            authSocketBound.countDown();
            return socket;
        }

        boolean awaitAuthSocketBound(long timeout, TimeUnit unit) throws InterruptedException {
            return authSocketBound.await(timeout, unit);
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
                    "test-radius",
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
