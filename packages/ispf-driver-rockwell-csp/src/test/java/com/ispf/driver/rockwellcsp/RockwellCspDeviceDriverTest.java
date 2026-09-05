package com.ispf.driver.rockwellcsp;

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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
 * Loopback tests for {@link RockwellCspDeviceDriver} against a fake CSP-lab TCP server.
 */
class RockwellCspDeviceDriverTest {

    private RockwellCspDeviceDriver driver;
    private FakeCspServer server;

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
    void metadataIsProductionReadWriteLabSubset() {
        RockwellCspDeviceDriver underTest = new RockwellCspDeviceDriver();
        assertEquals("rockwell-csp", underTest.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, underTest.metadata().maturity());
        assertEquals(Set.of("read", "write"), underTest.metadata().capabilities());
        assertEquals("2222", underTest.metadata().configurationSchema().get("port"));
        String description = underTest.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not ethernet/ip") || description.contains("not ether"));
    }

    @Test
    void readsAndWritesTypedFilesViaLoopback() throws Exception {
        server = new FakeCspServer();
        server.putInt("N7:0", 1234);
        server.putFloat("F8:1", 3.5f);
        server.putInt("B3:0", 0x0005);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port())
        ));
        driver = new RockwellCspDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "n", "N7:0",
                "f", "F8:1",
                "b", "B3:0/0"
        ));

        assertEquals("1234", object.variables.get("n").firstRow().get("value"));
        assertEquals("N7", object.variables.get("n").firstRow().get("file"));
        assertEquals("3.5", object.variables.get("f").firstRow().get("value"));
        assertEquals("1", object.variables.get("b").firstRow().get("value"));

        driver.writePoint("n", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 42)
        ));
        assertEquals(42, server.getInt("N7:0"));
        assertEquals("42", object.variables.get("n").firstRow().get("value"));

        driver.writePoint("f", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 9.25)
        ));
        assertEquals(9.25f, server.getFloat("F8:1"), 0.001f);
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(
                new RockwellCspPoint(RockwellCspPoint.FileType.N, 7, 0, 0),
                RockwellCspPoint.parse("N7:0")
        );
        assertEquals(
                new RockwellCspPoint(RockwellCspPoint.FileType.F, 8, 1, 0),
                RockwellCspPoint.parse("F8:1")
        );
        assertEquals(
                new RockwellCspPoint(RockwellCspPoint.FileType.B, 3, 0, 0),
                RockwellCspPoint.parse("B3:0/0")
        );
        assertEquals(
                new RockwellCspPoint(RockwellCspPoint.FileType.B, 3, 0, 2),
                RockwellCspPoint.parse("b3:0/2")
        );
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new RockwellCspDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "N7:0")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeCspServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-csp-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

        FakeCspServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putInt(String key, int value) {
            storage.put(key, RockwellCspFrame.encodeInt16(value));
        }

        void putFloat(String key, float value) {
            storage.put(key, RockwellCspFrame.encodeFloat(value));
        }

        int getInt(String key) {
            return RockwellCspFrame.decodeInt16(storage.getOrDefault(key, new byte[2]));
        }

        float getFloat(String key) {
            return RockwellCspFrame.decodeFloat(storage.getOrDefault(key, new byte[4]));
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
                    RockwellCspFrame.ParsedFrame req = RockwellCspFrame.readFrame(in);
                    out.write(buildReply(req));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException | RuntimeException ignored) {
            }
        }

        private byte[] buildReply(RockwellCspFrame.ParsedFrame req) {
            RockwellCspPoint point = RockwellCspFrame.parseAddress(req.payload());
            String key = storageKey(point);
            if (req.cmd() == RockwellCspFrame.CMD_TYPED_READ) {
                int size = req.payload().length > 5
                        ? (req.payload()[5] & 0xFF)
                        : RockwellCspFrame.elementSize(point);
                byte[] data = storage.getOrDefault(key, new byte[size]);
                if (data.length != size) {
                    data = Arrays.copyOf(data, size);
                }
                return RockwellCspFrame.buildReply(
                        req.cmd(), req.tns(), RockwellCspFrame.STS_OK, data);
            }
            if (req.cmd() == RockwellCspFrame.CMD_TYPED_WRITE) {
                int size = req.payload().length > 5 ? (req.payload()[5] & 0xFF) : 0;
                byte[] data = Arrays.copyOfRange(req.payload(), 6, 6 + size);
                storage.put(key, data);
                return RockwellCspFrame.buildReply(
                        req.cmd(), req.tns(), RockwellCspFrame.STS_OK, new byte[0]);
            }
            return RockwellCspFrame.buildReply(req.cmd(), req.tns(), (byte) 0x10, new byte[0]);
        }

        private static String storageKey(RockwellCspPoint point) {
            return point.fileType().token() + point.fileNumber() + ":" + point.element();
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
                    "test-rockwell-csp",
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
