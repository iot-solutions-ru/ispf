package com.ispf.driver.canbusgateway;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanbusGatewayDeviceDriverTest {

    private CanbusGatewayDeviceDriver driver;
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
    void standardFrameId001Dlc2Data1122IsExactLiteral() {
        byte[] expected = "t00121122\r".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(expected, CanbusGatewayDeviceDriver.formatId001Dlc2Data1122Literal()
                .getBytes(StandardCharsets.US_ASCII));
        assertEquals("t00121122\r", CanbusGatewayDeviceDriver.formatStandardFrame(0x001, "1122"));
        assertArrayEquals(expected,
                CanbusGatewayDeviceDriver.formatStandardFrame(0x001, "11 22")
                        .getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void metadataIsProductionSlcan() {
        driver = new CanbusGatewayDeviceDriver();
        assertEquals("canbus-gateway", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("slcan"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void readWriteLoopback() throws Exception {
        peer = new FakeSlcanPeer();
        peer.put(0x001, "1122");
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new CanbusGatewayDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("frame", "0x001"));
        assertEquals("1122", object.variables.get("frame").firstRow().get("value"));

        driver.writePoint("frame", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "AABB")
        ));
        assertEquals("AABB", peer.get(0x001));
        assertEquals("t00121122\r",
                CanbusGatewayDeviceDriver.formatStandardFrame(0x001, "1122"));
    }

    private static final class FakeSlcanPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-can-slcan");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, String> frames = new ConcurrentHashMap<>();

        FakeSlcanPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int canId, String data) {
            frames.put(canId & 0x7FF, CanbusGatewayDeviceDriver.normalizeHex(data));
        }

        String get(int canId) {
            return frames.get(canId & 0x7FF);
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
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    String line = CanbusGatewayDeviceDriver.readUntilCr(in);
                    CanbusGatewayDeviceDriver.SlcanFrame frame =
                            CanbusGatewayDeviceDriver.parseSlcanStandard(line);
                    if (frame.dataHex().isEmpty()) {
                        String data = frames.getOrDefault(frame.canId(), "");
                        CanbusGatewayDeviceDriver.writeAscii(out,
                                CanbusGatewayDeviceDriver.formatStandardFrame(frame.canId(), data));
                    } else {
                        frames.put(frame.canId(), frame.dataHex());
                        CanbusGatewayDeviceDriver.writeAscii(out, "z\r");
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // closed
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-can", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
