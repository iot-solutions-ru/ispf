package com.ispf.driver.profibuspa;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.profibuspa.codec.ProfibusPaFdlCodec;
import com.ispf.driver.profibuspa.codec.ProfibusPaFdlFrame;
import com.ispf.driver.profibuspa.codec.ProfibusPaFdlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process ServerSocket peer for PROFIBUS PA FDL over TCP (serial-server).
 * Certifies SD1/SD2 framing only — not native PA PHY / DP-PA coupler.
 */
class ProfibusPaDeviceDriverTest {

    private ProfibusPaDeviceDriver driver;
    private FakeProfibusPaFdlPeer peer;

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
    void metadataIsProductionReadWriteFdlOverTcp() {
        driver = new ProfibusPaDeviceDriver();
        assertEquals("profibus-pa", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("fdl"));
        assertTrue(description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertTrue(description.contains("pa"));
        assertTrue(!description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
        assertEquals("9600", driver.metadata().configurationSchema().get("port"));
    }

    @Test
    void pointParserAcceptsSlotAddrAndPaForms() throws Exception {
        ProfibusPaPoint slot = ProfibusPaPoint.parse("slot:1");
        assertEquals("slot:1", slot.wireToken());
        assertEquals("slot", slot.kind());
        assertEquals(1, slot.index());

        ProfibusPaPoint slotPv = ProfibusPaPoint.parse("slot:1:pv");
        assertEquals("slot:1:pv", slotPv.wireToken());
        assertEquals("slot-pv", slotPv.kind());
        assertEquals(1, slotPv.index());

        ProfibusPaPoint addr = ProfibusPaPoint.parse("addr:12");
        assertEquals("addr:12", addr.wireToken());
        assertEquals(12, addr.index());

        ProfibusPaPoint pa = ProfibusPaPoint.parse("pa:1");
        assertEquals("pa:1", pa.wireToken());
        assertEquals(1, pa.index());
    }

    @Test
    void readAndWriteFdlPoints() throws Exception {
        peer = new FakeProfibusPaFdlPeer();
        peer.put("slot:1", 10.0);
        peer.put("slot:1:pv", 12.5);
        peer.put("addr:12", 44.0);
        peer.put("pa:1", 1.0);
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        TestDriverObject object = new TestDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ProfibusPaDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "s1", "slot:1",
                "pv", "slot:1:pv",
                "a12", "addr:12",
                "pa1", "pa:1"
        ));
        assertEquals(10.0, (Double) object.variables.get("s1").firstRow().get("value"), 0.001);
        assertEquals(12.5, (Double) object.variables.get("pv").firstRow().get("value"), 0.001);
        assertEquals(44.0, (Double) object.variables.get("a12").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("pa1").firstRow().get("value"), 0.001);

        driver.writePoint("a12", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 55.5)
        ));
        assertEquals(55.5, peer.get("addr:12"), 0.001);
        assertEquals(55.5, (Double) object.variables.get("a12").firstRow().get("value"), 0.001);

        driver.writePoint("pv", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 99.1)
        ));
        assertEquals(99.1, peer.get("slot:1:pv"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ProfibusPaDeviceDriver();
        driver.initialize(new TestDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slot:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeProfibusPaFdlPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-profibus-pa-fdl");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeProfibusPaFdlPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String token, double value) {
            values.put(normalize(token), value);
        }

        double get(String token) {
            return values.getOrDefault(normalize(token), 0.0);
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
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    ProfibusPaFdlFrame frame = ProfibusPaFdlCodec.readFrame(in);
                    if (frame.kind() == ProfibusPaFdlFrame.Kind.SD1) {
                        writeFully(out, ProfibusPaFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusPaFdlTypes.FC_SRD,
                                ProfibusPaFdlCodec.encodeStatusPdu()));
                        continue;
                    }
                    if (frame.kind() != ProfibusPaFdlFrame.Kind.SD2) {
                        continue;
                    }
                    byte[] pdu = frame.pdu();
                    if (pdu.length == 0) {
                        continue;
                    }
                    int opcode = pdu[0] & 0xFF;
                    if (opcode == ProfibusPaFdlTypes.PDU_RD_REQ && pdu.length >= 4) {
                        int kindCode = pdu[1] & 0xFF;
                        int index = ByteBuffer.wrap(pdu, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        String kind = ProfibusPaFdlCodec.kindName(kindCode);
                        String token = ProfibusPaFdlCodec.tokenFor(kindCode, index);
                        double value = values.getOrDefault(token, 0.0);
                        writeFully(out, ProfibusPaFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusPaFdlTypes.FC_SRD,
                                ProfibusPaFdlCodec.encodeReadResponse(kind, index, value)));
                    } else if (opcode == ProfibusPaFdlTypes.PDU_WR_REQ && pdu.length >= 12) {
                        int kindCode = pdu[1] & 0xFF;
                        int index = ByteBuffer.wrap(pdu, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        String kind = ProfibusPaFdlCodec.kindName(kindCode);
                        double value = ByteBuffer.wrap(pdu, 4, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                        values.put(ProfibusPaFdlCodec.tokenFor(kindCode, index), value);
                        writeFully(out, ProfibusPaFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusPaFdlTypes.FC_SDA,
                                ProfibusPaFdlCodec.encodeWriteResponse(kind, index)));
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static String normalize(String token) {
            return token.trim().toLowerCase(Locale.ROOT).replace('=', ':');
        }

        private static void writeFully(OutputStream out, byte[] frame) throws IOException {
            out.write(frame);
            out.flush();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class TestDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        TestDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-profibus-pa",
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
