package com.ispf.driver.mitsubishimelsec;

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
 * Loopback tests for {@link MitsubishiMelsecDeviceDriver} against a fake MC Protocol 3E server.
 */
class MitsubishiMelsecDeviceDriverTest {

    private MitsubishiMelsecDeviceDriver driver;
    private FakeMelsecServer melsecServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (melsecServer != null) {
            melsecServer.close();
            melsecServer = null;
        }
    }

    @Test
    void readsAndWritesDRegistersViaLoopback() throws Exception {
        melsecServer = new FakeMelsecServer();
        melsecServer.put(100, 0x1234);
        melsecServer.put(101, 0x00AB);
        melsecServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(melsecServer.port())
        ));
        driver = new MitsubishiMelsecDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pair", "D100:2",
                "single", "D100"
        ));

        DataRecord pair = object.variables.get("pair");
        assertEquals("4660,171", pair.firstRow().get("value"));
        assertEquals("D", pair.firstRow().get("register"));
        assertEquals(100, ((Number) pair.firstRow().get("address")).intValue());
        assertEquals(2, ((Number) pair.firstRow().get("words")).intValue());
        assertEquals("4660", object.variables.get("single").firstRow().get("value"));

        driver.writePoint("single", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 777)
        ));
        assertEquals(777, melsecServer.get(100));
        assertEquals("777", object.variables.get("single").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(new MitsubishiMelsecPoint(100, 1), MitsubishiMelsecPoint.parse("D100"));
        assertEquals(new MitsubishiMelsecPoint(100, 2), MitsubishiMelsecPoint.parse("D100:2"));
        assertEquals(new MitsubishiMelsecPoint(50, 1), MitsubishiMelsecPoint.parse("d50"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new MitsubishiMelsecDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "D100")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeMelsecServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-melsec-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> words = new ConcurrentHashMap<>();

        FakeMelsecServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int address, int value) {
            words.put(address, value & 0xFFFF);
        }

        int get(int address) {
            return words.getOrDefault(address, 0);
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
                    byte[] header = new byte[9];
                    in.readFully(header);
                    int length = (header[7] & 0xFF) | ((header[8] & 0xFF) << 8);
                    byte[] body = new byte[length];
                    in.readFully(body);
                    out.write(buildResponse(header, body));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private byte[] buildResponse(byte[] requestHeader, byte[] body) {
            ByteBuffer req = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
            req.getShort(); // monitoring timer
            int command = req.getShort() & 0xFFFF;
            req.getShort(); // subcommand
            int address = (req.get() & 0xFF) | ((req.get() & 0xFF) << 8) | ((req.get() & 0xFF) << 16);
            req.get(); // device code
            int count = req.getShort() & 0xFFFF;

            ByteBuffer payload;
            if (command == MitsubishiMelsecFrame.CMD_BATCH_READ) {
                payload = ByteBuffer.allocate(2 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
                payload.putShort((short) 0);
                for (int i = 0; i < count; i++) {
                    payload.putShort((short) (int) words.getOrDefault(address + i, 0));
                }
            } else if (command == MitsubishiMelsecFrame.CMD_BATCH_WRITE) {
                for (int i = 0; i < count; i++) {
                    words.put(address + i, req.getShort() & 0xFFFF);
                }
                payload = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
                payload.putShort((short) 0);
            } else {
                payload = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
                payload.putShort((short) 0xC050);
            }

            byte[] data = payload.array();
            ByteBuffer frame = ByteBuffer.allocate(9 + data.length).order(ByteOrder.LITTLE_ENDIAN);
            frame.put((byte) 0xD0);
            frame.put((byte) 0x00);
            frame.put(requestHeader[2]);
            frame.put(requestHeader[3]);
            frame.put(requestHeader[4]);
            frame.put(requestHeader[5]);
            frame.put(requestHeader[6]);
            frame.putShort((short) data.length);
            frame.put(data);
            return frame.array();
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
                    "test-mitsubishi-melsec",
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
