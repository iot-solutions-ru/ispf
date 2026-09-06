package com.ispf.driver.lsxgt;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link LsXgtDeviceDriver} against a fake XGT-lab TCP server.
 */
class LsXgtDeviceDriverTest {

    private LsXgtDeviceDriver driver;
    private FakeXgtLabServer xgtServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (xgtServer != null) {
            xgtServer.close();
            xgtServer = null;
        }
    }

    @Test
    void readsAndWritesDeviceMemoryViaLoopback() throws Exception {
        xgtServer = new FakeXgtLabServer();
        xgtServer.put(LsXgtPoint.DeviceType.DW, 100, 0x1234);
        xgtServer.put(LsXgtPoint.DeviceType.DW, 101, 0x00AB);
        xgtServer.put(LsXgtPoint.DeviceType.MW, 10, 7);
        xgtServer.put(LsXgtPoint.DeviceType.MX, 0, 1);
        xgtServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(xgtServer.port())
        ));
        driver = new LsXgtDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("ls-xgt", driver.metadata().id());

        driver.readPoints(Map.of(
                "pair", "%DW100:2",
                "mw", "MW10",
                "bit", "%MX0"
        ));

        assertEquals("4660,171", object.variables.get("pair").firstRow().get("value"));
        assertEquals("DW", object.variables.get("pair").firstRow().get("device"));
        assertEquals("7", object.variables.get("mw").firstRow().get("value"));
        assertEquals("1", object.variables.get("bit").firstRow().get("value"));

        driver.writePoint("mw", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 99)
        ));
        assertEquals(99, xgtServer.get(LsXgtPoint.DeviceType.MW, 10));
        assertEquals("99", object.variables.get("mw").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 1),
                LsXgtPoint.parse("%DW100"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 1),
                LsXgtPoint.parse("DW100"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 2),
                LsXgtPoint.parse("%DW100:2"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.MW, 10, 1),
                LsXgtPoint.parse("%MW10"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.MX, 0, 1),
                LsXgtPoint.parse("%MX0"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new LsXgtDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "%DW1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeXgtLabServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-xgt-lab-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> memory = new ConcurrentHashMap<>();

        FakeXgtLabServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(LsXgtPoint.DeviceType type, int address, int value) {
            memory.put(key(type, address), value & 0xFFFF);
        }

        int get(LsXgtPoint.DeviceType type, int address) {
            return memory.getOrDefault(key(type, address), 0);
        }

        private static String key(LsXgtPoint.DeviceType type, int address) {
            return type.name() + ":" + address;
        }

        private static LsXgtPoint.DeviceType typeFromCode(byte code) {
            return switch (code) {
                case 0x01 -> LsXgtPoint.DeviceType.DW;
                case 0x02 -> LsXgtPoint.DeviceType.MW;
                case 0x03 -> LsXgtPoint.DeviceType.MX;
                default -> throw new IllegalArgumentException("Unknown device type " + code);
            };
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
                    byte[] header = new byte[LsXgtDeviceDriver.HEADER_LEN];
                    in.readFully(header);
                    if (!Arrays.equals(Arrays.copyOf(header, LsXgtDeviceDriver.MAGIC.length), LsXgtDeviceDriver.MAGIC)) {
                        return;
                    }
                    byte command = header[12];
                    LsXgtPoint.DeviceType type = typeFromCode(header[13]);
                    int address = ByteBuffer.wrap(header, 14, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    int count = ByteBuffer.wrap(header, 18, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

                    if (command == LsXgtDeviceDriver.CMD_WRITE) {
                        byte[] payload = new byte[count * 2];
                        in.readFully(payload);
                        ByteBuffer words = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i < count; i++) {
                            put(type, address + i, words.getShort() & 0xFFFF);
                        }
                        out.write(header);
                        out.flush();
                    } else if (command == LsXgtDeviceDriver.CMD_READ) {
                        ByteBuffer response = ByteBuffer.allocate(LsXgtDeviceDriver.HEADER_LEN + count * 2)
                                .order(ByteOrder.LITTLE_ENDIAN);
                        response.put(header);
                        for (int i = 0; i < count; i++) {
                            response.putShort((short) get(type, address + i));
                        }
                        out.write(response.array());
                        out.flush();
                    }
                }
            } catch (EOFException ignored) {
            } catch (IOException | RuntimeException ignored) {
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
                    "test-ls-xgt",
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
