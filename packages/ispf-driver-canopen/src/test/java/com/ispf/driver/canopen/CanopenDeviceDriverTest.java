package com.ispf.driver.canopen;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process peer tests for CANopen LAWICEL SLCAN over TCP.
 */
class CanopenDeviceDriverTest {

    private CanopenDeviceDriver driver;
    private FakeSlcanPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (peer != null) {
            peer.close();
            peer = null;
        }
    }

    @Test
    void sdoUploadRequestLiteralForNode1Index2000Sub0() {
        // id 0x601, DLC 8, data 40 00 20 00 00 00 00 00
        String expected = "t60184000200000000000\r";
        String line = CanopenDeviceDriver.formatSdoUploadRequest(1, 0x2000, 0);
        assertEquals(expected, line);
        assertTrue(expected.contains("t6018") && expected.contains("40002000"));
    }

    @Test
    void metadataIsProductionReadWriteSlcan() {
        driver = new CanopenDeviceDriver();
        assertEquals("canopen", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("slcan") || description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void readHexAndDecimalOdMappings() throws Exception {
        peer = new FakeSlcanPeer();
        peer.put(0x2000, 0x01, 42);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "nodeId", "1",
                "timeoutMs", "2000"
        ));
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("speed", "0x2000:01"));
        assertEquals("42", object.variables.get("speed").firstRow().get("value"));
        assertEquals("0x2000", object.variables.get("speed").firstRow().get("index"));
        assertEquals("01", object.variables.get("speed").firstRow().get("sub"));

        driver.readPoints(Map.of("speedDec", "2000:1"));
        assertEquals("42", object.variables.get("speedDec").firstRow().get("value"));
        assertEquals(0x2000, CanopenDeviceDriver.parseOdMapping("0x2000:01").index());
        assertEquals(1, CanopenDeviceDriver.parseOdMapping("0x2000:01").sub());
    }

    @Test
    void writeThenReadSdo() throws Exception {
        peer = new FakeSlcanPeer();
        peer.put(0x2000, 0x01, 0);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "nodeId", "1",
                "timeoutMs", "2000"
        ));
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("od", "0x2000:01"));
        driver.writePoint("od", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "99")
        ));
        assertEquals(99, peer.get(0x2000, 0x01));

        driver.readPoints(Map.of("od", "0x2000:01"));
        assertEquals("99", object.variables.get("od").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new CanopenDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "0x2000:01")));
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
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("CANopen SLCAN connect failed"));
    }

    private static final class FakeSlcanPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-canopen-slcan");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Long> values = new ConcurrentHashMap<>();

        FakeSlcanPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int index, int sub, long value) {
            values.put(key(index, sub), value);
        }

        long get(int index, int sub) {
            return values.getOrDefault(key(index, sub), 0L);
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
                    String line = CanopenDeviceDriver.readUntilCr(in);
                    CanopenDeviceDriver.SlcanFrame frame = CanopenDeviceDriver.tryParseSlcanStandard(line);
                    if (frame == null) {
                        out.write("\r".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                        continue;
                    }
                    String data = frame.dataHex();
                    if (data.length() < 8) {
                        out.write("\r".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                        continue;
                    }
                    int cmd = Integer.parseInt(data.substring(0, 2), 16);
                    int index = Integer.parseInt(data.substring(2, 4), 16)
                            | (Integer.parseInt(data.substring(4, 6), 16) << 8);
                    int sub = Integer.parseInt(data.substring(6, 8), 16);
                    int node = frame.canId() & 0x7F;
                    if (cmd == 0x40) {
                        long value = values.getOrDefault(key(index, sub), 0L);
                        // Expedited 4-byte upload response
                        String respData = String.format(Locale.ROOT, "43%02X%02X%02X%02X%02X%02X%02X",
                                index & 0xFF, (index >> 8) & 0xFF, sub & 0xFF,
                                (int) (value & 0xFF),
                                (int) ((value >> 8) & 0xFF),
                                (int) ((value >> 16) & 0xFF),
                                (int) ((value >> 24) & 0xFF));
                        String reply = CanopenDeviceDriver.formatStandardFrame(0x580 + node, respData);
                        out.write(reply.getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else if (cmd == 0x23) {
                        long value = Integer.parseInt(data.substring(8, 10), 16)
                                | (Integer.parseInt(data.substring(10, 12), 16) << 8)
                                | (Integer.parseInt(data.substring(12, 14), 16) << 16)
                                | ((long) Integer.parseInt(data.substring(14, 16), 16) << 24);
                        values.put(key(index, sub), value);
                        String respData = String.format(Locale.ROOT, "60%02X%02X%02X00000000",
                                index & 0xFF, (index >> 8) & 0xFF, sub & 0xFF);
                        String reply = CanopenDeviceDriver.formatStandardFrame(0x580 + node, respData);
                        out.write(reply.getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else {
                        out.write("\r".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static String key(int index, int sub) {
            return index + ":" + sub;
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
                    "test-canopen",
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
