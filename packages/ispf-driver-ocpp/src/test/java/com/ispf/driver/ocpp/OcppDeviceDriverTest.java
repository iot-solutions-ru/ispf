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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link OcppDeviceDriver} against an in-process fake CSMS
 * (newline-delimited OCPP 1.6 JSON CALL/CALLRESULT).
 */
class OcppDeviceDriverTest {

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
    void bootHeartbeatStatusViaLoopback() throws Exception {
        csms = new FakeCsms();
        csms.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(csms.port()),
                "timeoutMs", "2000",
                "chargePointVendor", "ISPF",
                "chargePointModel", "LabCP",
                "connectorStatus", "Available"
        ));
        driver = new OcppDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals(1, csms.bootCount());

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
            try (socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
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
                            out.write(OcppJson.callResult(call.uniqueId(), Map.of()));
                            out.write('\n');
                            out.flush();
                            continue;
                        }
                    }
                    out.write(OcppJson.callResult(call.uniqueId(), payload));
                    out.write('\n');
                    out.flush();
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
