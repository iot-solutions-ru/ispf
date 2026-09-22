package com.ispf.driver.j1939;

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
 * In-process peer tests for J1939 LAWICEL SLCAN over TCP.
 */
class J1939DeviceDriverTest {

    private J1939DeviceDriver driver;
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
    void metadataIsProductionReadWriteSlcan() {
        driver = new J1939DeviceDriver();
        assertEquals("j1939", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("slcan") || description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void readDecimalAndHexPgnMappings() throws Exception {
        peer = new FakeSlcanPeer();
        peer.put(61444, 238, "0F0A1B2C3D4E5F60");
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new J1939DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("engine", "PGN:61444"));
        assertEquals("0F0A1B2C3D4E5F60", object.variables.get("engine").firstRow().get("value"));
        assertEquals("0F0A1B2C3D4E5F60", object.variables.get("engine").firstRow().get("data"));
        assertEquals("238", object.variables.get("engine").firstRow().get("sa"));
        assertEquals("61444", object.variables.get("engine").firstRow().get("pgn"));

        driver.readPoints(Map.of("engineHex", "0xF004"));
        assertEquals("0F0A1B2C3D4E5F60", object.variables.get("engineHex").firstRow().get("data"));
        assertEquals(61444, J1939DeviceDriver.parsePgnMapping("0xF004"));
        assertEquals(61444, J1939DeviceDriver.parsePgnMapping("PGN:61444"));
    }

    @Test
    void writeHexThenNumericThenRead() throws Exception {
        peer = new FakeSlcanPeer();
        peer.put(61444, 0, "00");
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "sa", "16",
                "timeoutMs", "2000"
        ));
        driver = new J1939DeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("pgn", "PGN:61444"));
        driver.writePoint("pgn", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "DEADBEEF")
        ));
        assertEquals("DEADBEEF", peer.data(61444));
        assertEquals(16, peer.sa(61444));

        driver.writePoint("pgn", DataRecord.single(
                DataSchema.builder("v")
                        .field("value", FieldType.STRING)
                        .field("sa", FieldType.STRING)
                        .build(),
                Map.of("value", "255", "sa", "1")
        ));
        assertEquals("FF", peer.data(61444));
        assertEquals(1, peer.sa(61444));

        driver.readPoints(Map.of("pgn", "PGN:61444"));
        assertEquals("FF", object.variables.get("pgn").firstRow().get("value"));
        assertEquals("1", object.variables.get("pgn").firstRow().get("sa"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new J1939DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "PGN:61444")));
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
        driver = new J1939DeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("J1939 SLCAN connect failed"));
    }

    private static final class FakeSlcanPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-j1939-slcan");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, J1939DeviceDriver.Frame> frames = new ConcurrentHashMap<>();

        FakeSlcanPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int pgn, int sa, String dataHex) {
            frames.put(pgn, new J1939DeviceDriver.Frame(pgn, sa, J1939DeviceDriver.normalizeHex(dataHex)));
        }

        String data(int pgn) {
            J1939DeviceDriver.Frame frame = frames.get(pgn);
            return frame == null ? null : frame.dataHex();
        }

        int sa(int pgn) {
            return frames.get(pgn).sa();
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
                    String line = J1939DeviceDriver.readUntilCr(in);
                    J1939DeviceDriver.Frame incoming = J1939DeviceDriver.tryParseSlcanExtended(line);
                    if (incoming == null) {
                        out.write("\r".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                        continue;
                    }
                    if (incoming.dataHex().isEmpty()) {
                        J1939DeviceDriver.Frame stored = frames.get(incoming.pgn());
                        if (stored == null) {
                            out.write("\r".getBytes(StandardCharsets.US_ASCII));
                        } else {
                            int canId = J1939DeviceDriver.encodeCanId(6, stored.pgn(), stored.sa());
                            String reply = J1939DeviceDriver.formatExtendedFrame(canId, stored.dataHex());
                            out.write(reply.getBytes(StandardCharsets.US_ASCII));
                        }
                        out.flush();
                    } else {
                        frames.put(incoming.pgn(), incoming);
                        out.write("z\r".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
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
                    "test-j1939",
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
