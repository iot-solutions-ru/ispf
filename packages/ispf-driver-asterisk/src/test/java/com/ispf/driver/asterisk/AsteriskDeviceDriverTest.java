package com.ispf.driver.asterisk;

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
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link AsteriskDeviceDriver}: an in-test TCP server speaks
 * the AMI banner/login/action exchange and records what the driver sends.
 */
class AsteriskDeviceDriverTest {

    private ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private int port;
    private final BlockingQueue<String> receivedBlocks = new LinkedBlockingQueue<>();
    private volatile String loginReply = "Response: Success\r\nMessage: Authentication accepted\r\n\r\n";
    private volatile String actionReply = "Response: Success\r\nPing: Pong\r\n\r\n";
    private AsteriskDeviceDriver driver;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
            serverExecutor = null;
        }
    }

    @Test
    void pingFlowReportsPong() throws Exception {
        startAmiServer();
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("amiPing", "Action: Ping"));

        DataRecord record = driverObject.variables.get("amiPing");
        assertEquals(true, record.firstRow().get("success"));
        assertEquals("Pong", record.firstRow().get("value"));
        assertTrue(String.valueOf(record.firstRow().get("response")).contains("Ping: Pong"));

        String loginBlock = receivedBlocks.poll(5, TimeUnit.SECONDS);
        String actionBlock = receivedBlocks.poll(5, TimeUnit.SECONDS);
        assertNotNull(loginBlock);
        assertNotNull(actionBlock);
        assertTrue(loginBlock.contains("Action: Login"));
        assertTrue(loginBlock.contains("Username: manager"));
        assertTrue(loginBlock.contains("Secret: s3cret"));
        assertTrue(actionBlock.contains("Action: Ping"));
    }

    @Test
    void messageHeaderBecomesValue() throws Exception {
        actionReply = "Response: Success\r\nMessage: Channel status will follow\r\n\r\n";
        startAmiServer();
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("amiStatus", "Action: Status"));

        DataRecord record = driverObject.variables.get("amiStatus");
        assertEquals(true, record.firstRow().get("success"));
        assertEquals("Channel status will follow", record.firstRow().get("value"));
    }

    @Test
    void errorResponseIsMarkedFailure() throws Exception {
        actionReply = "Response: Error\r\nMessage: Permission denied\r\n\r\n";
        startAmiServer();
        StubDriverObject driverObject = newDriver();
        driver.connect();

        driver.readPoints(Map.of("amiPing", "Action: Ping"));

        DataRecord record = driverObject.variables.get("amiPing");
        assertEquals(false, record.firstRow().get("success"));
        assertEquals("Permission denied", record.firstRow().get("value"));
    }

    @Test
    void readPointsRequiresConnect() {
        driver = new AsteriskDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("amiPing", "Action: Ping")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeIsReadOnly() {
        driver = new AsteriskDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("amiPing", DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject newDriver() {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "username", "manager",
                "secret", "s3cret"
        ));
        driver = new AsteriskDeviceDriver();
        driver.initialize(driverObject);
        return driverObject;
    }

    private void startAmiServer() throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        port = serverSocket.getLocalPort();
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    handleConnection(serverSocket.accept());
                }
            } catch (SocketException ignored) {
                // server socket closed during tearDown
            } catch (Exception ignored) {
                // loopback test best effort
            }
        });
    }

    private void handleConnection(Socket client) {
        try (client) {
            client.setSoTimeout(15000);
            Writer out = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            out.write("Asterisk Call Manager/1.1\r\n\r\n");
            out.flush();
            receivedBlocks.add(readBlock(in));
            out.write(loginReply);
            out.flush();
            receivedBlocks.add(readBlock(in));
            out.write(actionReply);
            out.flush();
        } catch (Exception ignored) {
            // loopback test best effort
        }
    }

    private static String readBlock(BufferedReader in) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line).append('\n');
            if (line.isEmpty()) {
                break;
            }
        }
        return sb.toString();
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
                    "test-asterisk",
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
