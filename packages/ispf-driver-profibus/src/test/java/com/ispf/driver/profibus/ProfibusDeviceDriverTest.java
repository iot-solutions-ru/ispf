package com.ispf.driver.profibus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.profibus.codec.ProfibusFdlCodec;
import com.ispf.driver.profibus.codec.ProfibusFdlFrame;
import com.ispf.driver.profibus.codec.ProfibusFdlTypes;
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
 * In-process ServerSocket peer for PROFIBUS DP FDL over TCP (serial-server).
 * Certifies SD1/SD2 framing only — not RS-485 DP PHY / FDL ASIC.
 */
class ProfibusDeviceDriverTest {

    private ProfibusDeviceDriver driver;
    private FakeProfibusFdlPeer peer;

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
        driver = new ProfibusDeviceDriver();
        assertEquals("profibus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("9600", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("fdl"));
        assertTrue(description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsSlaveAndSlaveByteForms() throws Exception {
        ProfibusPoint slave = ProfibusPoint.parse("slave:3");
        assertEquals(3, slave.slave());
        assertEquals(0, slave.byteOffset());
        assertEquals("slave:3:byte:0", slave.wireToken());

        ProfibusPoint both = ProfibusPoint.parse("slave:3:byte:0");
        assertEquals(3, both.slave());
        assertEquals(0, both.byteOffset());
    }

    @Test
    void readAndWriteDpFdlBytes() throws Exception {
        peer = new FakeProfibusFdlPeer();
        peer.put(3, 0, 12.5);
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        TestDriverObject object = new TestDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ProfibusDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "s3", "slave:3",
                "s3b0", "slave:3:byte:0"
        ));
        assertEquals(12.5, (Double) object.variables.get("s3").firstRow().get("value"), 0.001);
        assertEquals(12.5, (Double) object.variables.get("s3b0").firstRow().get("value"), 0.001);

        driver.writePoint("s3b0", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25, peer.get(3, 0), 0.001);
        assertEquals(33.25, (Double) object.variables.get("s3b0").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ProfibusDeviceDriver();
        driver.initialize(new TestDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slave:3")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeProfibusFdlPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-profibus-fdl");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeProfibusFdlPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int slave, int byteOffset, double value) {
            values.put(key(slave, byteOffset), value);
        }

        double get(int slave, int byteOffset) {
            return values.getOrDefault(key(slave, byteOffset), 0.0);
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
                    ProfibusFdlFrame frame = ProfibusFdlCodec.readFrame(in);
                    if (frame.kind() == ProfibusFdlFrame.Kind.SD1) {
                        writeFully(out, ProfibusFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusFdlTypes.FC_SRD,
                                ProfibusFdlCodec.encodeStatusPdu()));
                        continue;
                    }
                    if (frame.kind() != ProfibusFdlFrame.Kind.SD2) {
                        continue;
                    }
                    byte[] pdu = frame.pdu();
                    if (pdu.length == 0) {
                        continue;
                    }
                    int opcode = pdu[0] & 0xFF;
                    if (opcode == ProfibusFdlTypes.PDU_RD_REQ && pdu.length >= 3) {
                        int slave = pdu[1] & 0xFF;
                        int byteOffset = pdu[2] & 0xFF;
                        double value = values.getOrDefault(key(slave, byteOffset), 0.0);
                        writeFully(out, ProfibusFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusFdlTypes.FC_SRD,
                                ProfibusFdlCodec.encodeReadResponse(slave, byteOffset, value)));
                    } else if (opcode == ProfibusFdlTypes.PDU_WR_REQ && pdu.length >= 11) {
                        int slave = pdu[1] & 0xFF;
                        int byteOffset = pdu[2] & 0xFF;
                        double value = ByteBuffer.wrap(pdu, 3, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                        values.put(key(slave, byteOffset), value);
                        writeFully(out, ProfibusFdlCodec.encodeSd2(
                                frame.sa(),
                                frame.da(),
                                ProfibusFdlTypes.FC_SDA,
                                ProfibusFdlCodec.encodeWriteResponse(slave, byteOffset)));
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static String key(int slave, int byteOffset) {
            return slave + ":" + byteOffset;
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
                    "test-profibus",
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
