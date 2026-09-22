package com.ispf.driver.ocpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link OcppDeviceDriver} against an in-process fake CSMS
 * (RFC 6455 WebSocket + OCPP 1.6 JSON CALL/CALLRESULT).
 */
class OcppDeviceDriverTest {

    /**
     * Locked RFC 6455 example nonce upgrade — must stay a handwritten literal (not encoder-built).
     */
    private static final String RFC6455_UPGRADE =
            "GET /ocpp HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: ocpp1.6\r\n\r\n";

    private OcppDeviceDriver driver;
    private FakeCsms csms;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (csms != null) {
            csms.close();
            csms = null;
        }
    }

    @Test
    void websocketHandshakeAndMaskedHeartbeatFrame() throws Exception {
        CountDownLatch handshakeDone = new CountDownLatch(1);
        AtomicReference<String> capturedUpgrade = new AtomicReference<>();
        AtomicReference<byte[]> capturedHeartbeatFrame = new AtomicReference<>();
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ocpp-ws-peer");
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    String upgrade = readHttpRequest(in);
                    capturedUpgrade.set(upgrade);
                    out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"
                            + "Sec-WebSocket-Protocol: ocpp1.6\r\n"
                            + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    handshakeDone.countDown();
                    byte[] frame = readMaskedClientFrame(in);
                    capturedHeartbeatFrame.set(frame);
                    String payload = unmaskTextPayload(frame);
                    OcppJson.ParsedMessage call = OcppJson.parse(payload);
                    writeUnmaskedText(out, OcppJson.callResult(call.uniqueId(), Map.of(
                            "currentTime", Instant.parse("2026-09-05T12:00:00Z").toString()
                    )));
                } catch (IOException ignored) {
                }
            });

            StubDriverObject object = new StubDriverObject(Map.of(
                    "host", "127.0.0.1",
                    "port", String.valueOf(serverSocket.getLocalPort()),
                    "timeoutMs", "2000"
            ));
            driver = new OcppDeviceDriver();
            driver.initialize(object);
            driver.openWebSocket();
            assertTrue(handshakeDone.await(2, TimeUnit.SECONDS));
            assertEquals(RFC6455_UPGRADE, capturedUpgrade.get());

            driver.connectedForTest();
            driver.readPoints(Map.of("hb", "heartbeat"));

            byte[] expectedPayload = "[2,\"1\",\"Heartbeat\",{}]".getBytes(StandardCharsets.US_ASCII);
            assertEquals(22, expectedPayload.length);
            byte[] expectedFrame = new byte[6 + 22];
            expectedFrame[0] = (byte) 0x81;
            expectedFrame[1] = (byte) 0x96;
            expectedFrame[2] = 0x00;
            expectedFrame[3] = 0x00;
            expectedFrame[4] = 0x00;
            expectedFrame[5] = 0x00;
            System.arraycopy(expectedPayload, 0, expectedFrame, 6, 22);
            assertArrayEquals(expectedFrame, capturedHeartbeatFrame.get());
            assertEquals("Heartbeat", object.variables.get("hb").firstRow().get("action"));
        } finally {
            serverSocket.close();
            executor.shutdownNow();
        }
    }

    @Test
    void bootHeartbeatStatusViaLoopback() throws Exception {
        csms = new FakeCsms();
        csms.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(csms.port()),
                "timeoutMs", "2000",
                "chargePointVendor", "ISPF",
                "chargePointModel", "ISPF-CP",
                "connectorStatus", "Available"
        ));
        driver = new OcppDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals(1, csms.bootCount());
        assertEquals(RFC6455_UPGRADE, csms.upgradeRequest());

        driver.readPoints(Map.of(
                "hb", "heartbeat",
                "st", "status",
                "boot", "BootNotification"
        ));
        assertEquals("Heartbeat", object.variables.get("hb").firstRow().get("action"));
        assertTrue(String.valueOf(object.variables.get("hb").firstRow().get("value")).length() > 5);
        assertEquals("Available", object.variables.get("st").firstRow().get("value"));
        assertEquals("Accepted", object.variables.get("boot").firstRow().get("value"));
        assertTrue(csms.heartbeatCount() >= 1);
        assertTrue(csms.statusCount() >= 1);

        driver.writePoint("st", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "Charging")
        ));
        assertEquals("Charging", object.variables.get("st").firstRow().get("value"));
        assertEquals("Charging", csms.lastConnectorStatus());
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OcppDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("hb", "heartbeat")));
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
        driver = new OcppDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("OCPP connect failed"));
    }

    @Test
    void jsonCallRoundTrip() {
        String encoded = OcppJson.call("1", "Heartbeat", Map.of());
        assertEquals("[2,\"1\",\"Heartbeat\",{}]", encoded);
        OcppJson.ParsedMessage parsed = OcppJson.parse(
                OcppJson.callResult("1", Map.of("currentTime", "2026-09-05T00:00:00Z")));
        assertEquals(3, parsed.type());
        assertEquals("1", parsed.uniqueId());
        assertEquals("2026-09-05T00:00:00Z", parsed.payload().get("currentTime"));
    }

    private static String readHttpRequest(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            buf.write(b);
            if (state == 0 && b == '\r') {
                state = 1;
            } else if (state == 1 && b == '\n') {
                state = 2;
            } else if (state == 2 && b == '\r') {
                state = 3;
            } else if (state == 3 && b == '\n') {
                state = 4;
            } else {
                state = b == '\r' ? 1 : 0;
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private static byte[] readMaskedClientFrame(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new IOException("EOF reading client frame header");
        }
        int len = b1 & 0x7F;
        ByteArrayOutputStream buf = new ByteArrayOutputStream(6 + len);
        buf.write(b0);
        buf.write(b1);
        byte[] rest = in.readNBytes(4 + len);
        if (rest.length != 4 + len) {
            throw new IOException("EOF reading client frame body");
        }
        buf.write(rest);
        return buf.toByteArray();
    }

    private static String unmaskTextPayload(byte[] frame) {
        int len = frame[1] & 0x7F;
        byte[] mask = Arrays.copyOfRange(frame, 2, 6);
        byte[] payload = Arrays.copyOfRange(frame, 6, 6 + len);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void writeUnmaskedText(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > 125) {
            throw new IOException("frame too large");
        }
        out.write(0x81);
        out.write(payload.length);
        out.write(payload);
        out.flush();
    }

    private static final class FakeCsms implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ocpp-csms");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicInteger bootCount = new AtomicInteger();
        private final AtomicInteger heartbeatCount = new AtomicInteger();
        private final AtomicInteger statusCount = new AtomicInteger();
        private volatile String lastConnectorStatus = "";
        private volatile String upgradeRequest = "";

        FakeCsms() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int bootCount() {
            return bootCount.get();
        }

        int heartbeatCount() {
            return heartbeatCount.get();
        }

        int statusCount() {
            return statusCount.get();
        }

        String lastConnectorStatus() {
            return lastConnectorStatus;
        }

        String upgradeRequest() {
            return upgradeRequest;
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
                upgradeRequest = readHttpRequest(in);
                out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"
                        + "Sec-WebSocket-Protocol: ocpp1.6\r\n"
                        + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                while (true) {
                    byte[] frame = readMaskedClientFrame(in);
                    String line = unmaskTextPayload(frame);
                    OcppJson.ParsedMessage call = OcppJson.parse(line);
                    if (call.type() != 2) {
                        continue;
                    }
                    Map<String, String> payload = new ConcurrentHashMap<>();
                    String now = Instant.parse("2026-09-05T12:00:00Z").toString();
                    switch (call.action()) {
                        case "BootNotification" -> {
                            bootCount.incrementAndGet();
                            payload.put("status", "Accepted");
                            payload.put("currentTime", now);
                            payload.put("interval", "300");
                        }
                        case "Heartbeat" -> {
                            heartbeatCount.incrementAndGet();
                            payload.put("currentTime", now);
                        }
                        case "StatusNotification" -> {
                            statusCount.incrementAndGet();
                            lastConnectorStatus = call.payload().getOrDefault("status", "");
                        }
                        default -> {
                            writeUnmaskedText(out, OcppJson.callResult(call.uniqueId(), Map.of()));
                            continue;
                        }
                    }
                    writeUnmaskedText(out, OcppJson.callResult(call.uniqueId(), payload));
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
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
                    "test-ocpp",
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
