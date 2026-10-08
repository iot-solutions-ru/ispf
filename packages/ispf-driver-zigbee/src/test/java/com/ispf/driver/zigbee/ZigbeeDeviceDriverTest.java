package com.ispf.driver.zigbee;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.zigbee.codec.AshCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process ASH (EZSP UART) over TCP peer tests — not 802.15.4 / not ZCL.
 */
class ZigbeeDeviceDriverTest {

    private ZigbeeDeviceDriver driver;
    private FakeAshPeer peer;

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
    void hostResetFrameAndHalCommonCrc16() {
        // CRC via the bit function — not a copied table.
        assertEquals(0x38BC, AshCodec.crc16(new byte[] {(byte) 0xC0}));
        assertEquals(0x9B7B, AshCodec.crc16(new byte[] {(byte) 0xC1, 0x02, 0x02}));

        // Handwritten host RST octets — not built by copying encoder output into the expected array.
        byte[] expectedReset = new byte[] {
                0x1A, (byte) 0xC0, 0x38, (byte) 0xBC, 0x7E
        };
        assertArrayEquals(expectedReset, AshCodec.encodeHostReset());
        assertArrayEquals(expectedReset, ZigbeeDeviceDriver.hostResetFrame());
    }

    @Test
    void metadataIsProductionAshOverTcp() {
        driver = new ZigbeeDeviceDriver();
        assertEquals("zigbee", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertEquals("17754", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("ash"));
        assertFalse(description.contains("lab"));
        assertTrue(description.contains("not") && description.contains("802.15.4"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsVersionAndReason() throws Exception {
        assertEquals(ZigbeePoint.Kind.VERSION, ZigbeePoint.parse("version").kind());
        assertEquals(ZigbeePoint.Kind.REASON, ZigbeePoint.parse("reason").kind());
        assertEquals("rstack:version", ZigbeePoint.parse("rstack:version").display());
        assertEquals("rstack:reason", ZigbeePoint.parse("rstack:reason").display());
    }

    @Test
    void connectRstAndReadRstackVersionReasonLoopback() throws Exception {
        peer = new FakeAshPeer();
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ZigbeeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "ver", "version",
                "why", "reason"
        ));
        assertEquals(2.0, (Double) object.variables.get("ver").firstRow().get("value"), 0.001);
        assertEquals(2.0, (Double) object.variables.get("why").firstRow().get("value"), 0.001);
    }

    @Test
    void writeRejectedReadOnly() throws Exception {
        peer = new FakeAshPeer();
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ZigbeeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("ver", "version"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("ver", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("v")
                                .field("value", com.ispf.core.model.FieldType.DOUBLE)
                                .build(),
                        Map.of("value", 0.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("read-only")
                || error.getMessage().toLowerCase(Locale.ROOT).contains("ash"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ZigbeeDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "version")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * In-process ASH peer: on host RST {@code 1A C0 38 BC 7E}, replies
     * RSTACK {@code C1 02 02 9B 7B 7E} (CRC verified in production codec before trust).
     */
    private static final class FakeAshPeer implements AutoCloseable {

        private static final byte[] HOST_RST = {
                0x1A, (byte) 0xC0, 0x38, (byte) 0xBC, 0x7E
        };
        private static final byte[] RSTACK = {
                (byte) 0xC1, 0x02, 0x02, (byte) 0x9B, 0x7B, 0x7E
        };

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-zigbee-ash");
            thread.setDaemon(true);
            return thread;
        });

        FakeAshPeer() throws IOException {
            // Trust RSTACK CRC with the same bit function before serving it.
            assertEquals(0x9B7B, AshCodec.crc16(new byte[] {(byte) 0xC1, 0x02, 0x02}));
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void start() {
            var _ = executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    var _ = executor.submit(() -> handle(socket));
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
                    byte[] request = new byte[HOST_RST.length];
                    int offset = 0;
                    while (offset < request.length) {
                        int n = in.read(request, offset, request.length - offset);
                        if (n < 0) {
                            return;
                        }
                        offset += n;
                    }
                    assertArrayEquals(HOST_RST, request);
                    out.write(RSTACK);
                    out.flush();
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
                    "test-zigbee",
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
