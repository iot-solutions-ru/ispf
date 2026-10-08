package com.ispf.driver.thread;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.thread.codec.SpinelHdlcCodec;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ServerSocket peer tests for OpenThread Spinel HDLC over TCP (not 802.15.4).
 */
class ThreadDeviceDriverTest {

    private ThreadDeviceDriver driver;
    private FakeSpinelPeer peer;

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
    void cmdResetFrameIsLiteral7E800102927E() {
        // Handwritten Spinel CMD_RESET HDLC frame (not produced by the encoder for expected)
        byte[] expected = new byte[]{0x7E, (byte) 0x80, 0x01, 0x02, (byte) 0x92, 0x7E};
        assertArrayEquals(expected, SpinelHdlcCodec.encodeCmdReset());
        assertArrayEquals(expected, ThreadDeviceDriver.buildCmdResetFrame());
    }

    @Test
    void cmdResetCrcMatchesIndependentBitLoop() {
        // Independent CRC-16/ISO-HDLC bit loop (not a production lookup table copy)
        byte[] payload = new byte[]{(byte) 0x80, 0x01};
        int crc = 0xFFFF;
        for (byte value : payload) {
            crc ^= (value & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc >>>= 1;
                }
                crc &= 0xFFFF;
            }
        }
        crc = (crc ^ 0xFFFF) & 0xFFFF;
        assertEquals(0x9202, crc);
        assertEquals(0x02, crc & 0xFF);
        assertEquals(0x92, (crc >> 8) & 0xFF);
        assertEquals(0x9202, SpinelHdlcCodec.crc16IsoHdlc(payload));
    }

    @Test
    void metadataDescribesSpinelWithoutLab() {
        driver = new ThreadDeviceDriver();
        assertEquals("thread", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertEquals("8081", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("spinel"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsResetForms() throws Exception {
        assertEquals("reset", ThreadPoint.parse("reset").display());
        assertEquals("reset", ThreadPoint.parse("cmd:reset").display());
    }

    @Test
    void cmdResetLoopbackOverTcp() throws Exception {
        peer = new FakeSpinelPeer();
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ThreadDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("rcp", "reset"));
        assertEquals(1.0, (Double) object.variables.get("rcp").firstRow().get("value"), 0.001);
        assertEquals("reset", object.variables.get("rcp").firstRow().get("kind"));
        assertTrue(peer.awaitFrame(2, TimeUnit.SECONDS));
        assertArrayEquals(
                new byte[]{0x7E, (byte) 0x80, 0x01, 0x02, (byte) 0x92, 0x7E},
                peer.lastFrame()
        );

        assertThrows(DriverException.class, () ->
                driver.writePoint("rcp", object.variables.get("rcp")));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ThreadDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "reset")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeSpinelPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-spinel");
            thread.setDaemon(true);
            return thread;
        });
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch frameSeen = new CountDownLatch(1);
        private final AtomicReference<byte[]> lastFrame = new AtomicReference<>(new byte[0]);

        FakeSpinelPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        byte[] lastFrame() {
            return lastFrame.get();
        }

        void start() {
            var _ = executor.submit(this::acceptOnce);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean awaitFrame(long timeout, TimeUnit unit) throws InterruptedException {
            return frameSeen.await(timeout, unit);
        }

        private void acceptOnce() {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                byte[] frame = new byte[6];
                int offset = 0;
                while (offset < frame.length) {
                    int n = in.read(frame, offset, frame.length - offset);
                    if (n < 0) {
                        return;
                    }
                    offset += n;
                }
                lastFrame.set(frame);
                frameSeen.countDown();
                // Peer acknowledges by echoing the same CMD_RESET frame (no separate reply published)
                out.write(frame);
                out.flush();
            } catch (IOException ignored) {
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
        private final Map<String, DataRecord> variables = new java.util.concurrent.ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-thread",
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
