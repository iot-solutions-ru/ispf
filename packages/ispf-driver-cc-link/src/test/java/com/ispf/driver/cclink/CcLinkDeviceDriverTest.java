package com.ispf.driver.cclink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.cclink.codec.Slmp3eCodec;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process peer tests for CC-Link MELSEC SLMP 3E binary over TCP.
 */
class CcLinkDeviceDriverTest {

    private CcLinkDeviceDriver driver;
    private FakeSlmpPeer peer;

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
    void readD100RequestMatchesSlmp3eLiteral() {
        byte[] expected = new byte[] {
                0x50, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x03, 0x00, 0x0C, 0x00,
                0x10, 0x00, 0x01, 0x04, 0x00, 0x00, (byte) 0xA8, 0x64, 0x00, 0x00, 0x01, 0x00
        };
        assertArrayEquals(expected, Slmp3eCodec.buildReadD100Reference());
        assertArrayEquals(expected, CcLinkDeviceDriver.buildReadD100ReferenceFrame());
    }

    @Test
    void metadataIsProductionReadWriteSlmp() {
        driver = new CcLinkDeviceDriver();
        assertEquals("cc-link", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("5001", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("slmp") || description.contains("melsec"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsDRWAndDevForms() throws Exception {
        assertEquals("D100", CcLinkPoint.parse("D100").wireToken());
        assertEquals("R0", CcLinkPoint.parse("R0").wireToken());
        assertEquals("W0", CcLinkPoint.parse("W0").wireToken());
        assertEquals("D100", CcLinkPoint.parse("dev:D100").wireToken());
        assertEquals("D", CcLinkPoint.parse("dev:D100").kind());
        assertEquals(100, CcLinkPoint.parse("D100").address());
        assertEquals(Slmp3eCodec.DEVICE_R, Slmp3eCodec.deviceCodeByte("R"));
    }

    @Test
    void readAndWriteSlmpRegisters() throws Exception {
        peer = new FakeSlmpPeer();
        peer.put("D", 100, 12);
        peer.put("R", 0, 1);
        peer.put("W", 0, 7);
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new CcLinkDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "d100", "D100",
                "r0", "R0",
                "w0", "W0",
                "dev", "dev:D100"
        ));
        assertEquals(12.0, (Double) object.variables.get("d100").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("r0").firstRow().get("value"), 0.001);
        assertEquals(7.0, (Double) object.variables.get("w0").firstRow().get("value"), 0.001);
        assertEquals(12.0, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);

        driver.writePoint("d100", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.0)
        ));
        assertEquals(33, peer.get("D", 100));
        assertEquals(33.0, (Double) object.variables.get("d100").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new CcLinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "D100")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeSlmpPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-cc-link-slmp");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeSlmpPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String device, int address, int value) {
            values.put(key(device, address), value & 0xFFFF);
        }

        int get(String device, int address) {
            return values.getOrDefault(key(device, address), 0);
        }

        void start() {
            executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
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
            req.getShort();
            int command = req.getShort() & 0xFFFF;
            req.getShort();
            int deviceByte = req.get() & 0xFF;
            int address = (req.get() & 0xFF) | ((req.get() & 0xFF) << 8) | ((req.get() & 0xFF) << 16);
            int count = req.getShort() & 0xFFFF;
            String device = deviceName(deviceByte);

            ByteBuffer payload;
            if (command == Slmp3eCodec.CMD_BATCH_READ) {
                payload = ByteBuffer.allocate(2 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
                payload.putShort((short) 0);
                for (int i = 0; i < count; i++) {
                    payload.putShort((short) (int) values.getOrDefault(key(device, address + i), 0));
                }
            } else if (command == Slmp3eCodec.CMD_BATCH_WRITE) {
                for (int i = 0; i < count; i++) {
                    values.put(key(device, address + i), req.getShort() & 0xFFFF);
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

        private static String deviceName(int deviceByte) {
            if (deviceByte == Slmp3eCodec.DEVICE_D) {
                return "D";
            }
            if (deviceByte == Slmp3eCodec.DEVICE_R) {
                return "R";
            }
            if (deviceByte == Slmp3eCodec.DEVICE_W) {
                return "W";
            }
            return "D";
        }

        private static String key(String device, int address) {
            return device.toUpperCase(Locale.ROOT) + ":" + address;
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
                    "test-cc-link",
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
