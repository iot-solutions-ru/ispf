package com.ispf.driver.gesrtp;

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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
 * Loopback tests for {@link GeSrtpDeviceDriver} against a fake SRTP-lab MAILBOX TCP server.
 */
class GeSrtpDeviceDriverTest {

    private GeSrtpDeviceDriver driver;
    private FakeGeSrtpServer server;

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
    void metadataIsProductionReadWrite() {
        GeSrtpDeviceDriver underTest = new GeSrtpDeviceDriver();
        assertEquals("ge-srtp", underTest.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, underTest.metadata().maturity());
        assertEquals(Set.of("read", "write"), underTest.metadata().capabilities());
    }

    @Test
    void readsAndWritesMemoryViaLoopback() throws Exception {
        server = new FakeGeSrtpServer();
        server.put(GeSrtpPoint.GeSrtpMemoryType.R, 100, 0x1234);
        server.put(GeSrtpPoint.GeSrtpMemoryType.R, 101, 0x00AB);
        server.put(GeSrtpPoint.GeSrtpMemoryType.AI, 1, 42);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port())
        ));
        driver = new GeSrtpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "regs", "%R100:2",
                "ai", "AI1",
                "single", "R100"
        ));

        assertEquals("4660,171", object.variables.get("regs").firstRow().get("value"));
        assertEquals("R", object.variables.get("regs").firstRow().get("memory"));
        assertEquals(100, ((Number) object.variables.get("regs").firstRow().get("address")).intValue());
        assertEquals(2, ((Number) object.variables.get("regs").firstRow().get("count")).intValue());
        assertEquals("42", object.variables.get("ai").firstRow().get("value"));
        assertEquals("4660", object.variables.get("single").firstRow().get("value"));

        driver.writePoint("single", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 777)
        ));
        assertEquals(777, server.get(GeSrtpPoint.GeSrtpMemoryType.R, 100));
        assertEquals("777", object.variables.get("single").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.R, 100, 1),
                GeSrtpPoint.parse("%R100")
        );
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.R, 100, 1),
                GeSrtpPoint.parse("R100")
        );
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.AI, 1, 1),
                GeSrtpPoint.parse("%AI1")
        );
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.AQ, 2, 3),
                GeSrtpPoint.parse("%AQ2:3")
        );
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.I, 10, 1),
                GeSrtpPoint.parse("%I10")
        );
        assertEquals(
                new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.Q, 5, 1),
                GeSrtpPoint.parse("Q5")
        );
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new GeSrtpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "%R100")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeGeSrtpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ge-srtp-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> words = new ConcurrentHashMap<>();

        FakeGeSrtpServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(GeSrtpPoint.GeSrtpMemoryType type, int address, int value) {
            words.put(key(type, address), value & 0xFFFF);
        }

        int get(GeSrtpPoint.GeSrtpMemoryType type, int address) {
            return words.getOrDefault(key(type, address), 0);
        }

        private static String key(GeSrtpPoint.GeSrtpMemoryType type, int address) {
            return type.token() + ":" + address;
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
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                while (true) {
                    int length = in.readUnsignedShort();
                    byte[] body = new byte[length];
                    in.readFully(body);
                    out.write(buildResponse(body));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException | RuntimeException ignored) {
            }
        }

        private byte[] buildResponse(byte[] body) {
            try {
                GeSrtpFrame.ParsedRequest request = GeSrtpFrame.parseRequest(body);
                GeSrtpPoint point = request.point();
                if (request.command() == GeSrtpFrame.CMD_READ) {
                    int[] data = new int[point.count()];
                    for (int i = 0; i < point.count(); i++) {
                        data[i] = get(point.memoryType(), point.address() + i);
                    }
                    return GeSrtpFrame.buildOkResponse(data);
                }
                if (request.command() == GeSrtpFrame.CMD_WRITE) {
                    int[] writeWords = request.writeWords();
                    for (int i = 0; i < point.count(); i++) {
                        put(point.memoryType(), point.address() + i, writeWords[i]);
                    }
                    return GeSrtpFrame.buildOkResponse(new int[0]);
                }
                return GeSrtpFrame.buildErrorResponse((byte) 0x01);
            } catch (RuntimeException e) {
                return GeSrtpFrame.buildErrorResponse((byte) 0x02);
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
                    "test-ge-srtp",
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
