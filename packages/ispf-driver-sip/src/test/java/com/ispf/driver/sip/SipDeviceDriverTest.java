package com.ispf.driver.sip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link SipDeviceDriver}: a real UDP responder receives the
 * driver's OPTIONS/REGISTER datagrams and replies with a raw SIP status line.
 */
class SipDeviceDriverTest {

    private DatagramSocket responderSocket;
    private ExecutorService responderExecutor;
    private int responderPort;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private SipDeviceDriver driver;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (responderSocket != null) {
            responderSocket.close();
            responderSocket = null;
        }
        if (responderExecutor != null) {
            responderExecutor.shutdownNow();
            responderExecutor.awaitTermination(2, TimeUnit.SECONDS);
            responderExecutor = null;
        }
    }

    /**
     * Happy path: the OPTIONS probe builds the request via JAIN-SIP factories and exchanges
     * it over a raw UDP socket whose ephemeral local port is advertised in the Via header.
     */
    @Test
    void optionsOkIsReportedAsReachable() throws Exception {
        startUdpResponder("SIP/2.0 200 OK\r\nContent-Length: 0\r\n\r\n");
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("sipOptions", "options"));

        DataRecord record = driverObject.variables.get("sipOptions");
        assertEquals(true, record.firstRow().get("reachable"));
        assertEquals(200, record.firstRow().get("statusCode"));
        assertTrue(String.valueOf(record.firstRow().get("value")).startsWith("SIP/2.0 200 OK"));
        assertTrue(lastRequest.get().startsWith("OPTIONS sip:"));
    }

    @Test
    void registerChallengeIsReportedAsReachable() throws Exception {
        startUdpResponder("SIP/2.0 401 Unauthorized\r\nWWW-Authenticate: Digest realm=\"loopback.test\"\r\n\r\n");
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("sipRegister", "register"));

        DataRecord record = driverObject.variables.get("sipRegister");
        assertEquals(true, record.firstRow().get("reachable"));
        assertEquals(401, record.firstRow().get("statusCode"));
        assertEquals("challenge", record.firstRow().get("value"));
        assertTrue(lastRequest.get().startsWith("REGISTER sip:loopback.test"));
    }

    @Test
    void registerOkIsReportedAsRegistered() throws Exception {
        startUdpResponder("SIP/2.0 200 OK\r\nContent-Length: 0\r\n\r\n");
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("sipRegister", "register"));

        DataRecord record = driverObject.variables.get("sipRegister");
        assertEquals(true, record.firstRow().get("reachable"));
        assertEquals(200, record.firstRow().get("statusCode"));
        assertEquals("registered", record.firstRow().get("value"));
    }

    @Test
    void readPointsRequiresConnect() {
        driver = new SipDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sipOptions", "options")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeIsReadOnly() {
        driver = new SipDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("sipOptions", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject newDriver() {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(responderPort),
                "username", "alice",
                "domain", "loopback.test",
                "timeoutMs", "3000"
        ));
        driver = new SipDeviceDriver();
        driver.initialize(driverObject);
        return driverObject;
    }

    private void startUdpResponder(String responseText) throws Exception {
        responderSocket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        responderSocket.setSoTimeout(15000);
        responderPort = responderSocket.getLocalPort();
        responderExecutor = Executors.newSingleThreadExecutor();
        responderExecutor.submit(() -> {
            try {
                while (!responderSocket.isClosed()) {
                    byte[] buffer = new byte[8192];
                    DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                    responderSocket.receive(requestPacket);
                    lastRequest.set(new String(requestPacket.getData(), 0, requestPacket.getLength(),
                            StandardCharsets.UTF_8));
                    byte[] payload = responseText.getBytes(StandardCharsets.UTF_8);
                    responderSocket.send(new DatagramPacket(payload, payload.length,
                            requestPacket.getSocketAddress()));
                }
            } catch (SocketException ignored) {
                // socket closed during tearDown
            } catch (Exception ignored) {
                // loopback test best effort
            }
        });
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
                    "test-sip",
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
