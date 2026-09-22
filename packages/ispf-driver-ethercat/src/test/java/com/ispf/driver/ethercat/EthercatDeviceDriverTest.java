package com.ispf.driver.ethercat;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.ethercat.codec.EthercatCodec;
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
 * In-process peer tests for EtherCAT datagrams over TCP.
 */
class EthercatDeviceDriverTest {

    private EthercatDeviceDriver driver;
    private FakeEthercatPeer peer;

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
    void lrdReferenceMatchesDatagramLiteral() {
        byte[] frame = EthercatCodec.buildLrdReference();
        assertArrayEquals(new byte[] {
                0x0A, 0x01, 0x00, 0x00, 0x00, 0x10, 0x02, 0x00, 0x00, 0x00
        }, Arrays.copyOf(frame, 10));
        assertEquals(14, frame.length);
        assertEquals(0, frame[10] & 0xFF);
        assertEquals(0, frame[11] & 0xFF);
        assertEquals(0, frame[12] & 0xFF);
        assertEquals(0, frame[13] & 0xFF);
        assertArrayEquals(frame, EthercatDeviceDriver.buildLrdReferenceFrame());
    }

    @Test
    void metadataIsProductionReadWriteDatagram() {
        driver = new EthercatDeviceDriver();
        assertEquals("ethercat", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("34980", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("datagram") || description.contains("ethercat"));
        assertTrue(description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsSlavePdoAndObjectForms() throws Exception {
        EthercatPoint slave = EthercatPoint.parse("slave:1");
        assertEquals("slave:1", slave.wireToken());
        assertEquals(EthercatPoint.Kind.SLAVE, slave.kind());
        assertEquals(0x1000, slave.ado());

        EthercatPoint pdo = EthercatPoint.parse("slave:1:pdo:0");
        assertEquals("slave:1:pdo:0", pdo.wireToken());
        assertEquals(EthercatPoint.Kind.SLAVE_PDO, pdo.kind());

        EthercatPoint object = EthercatPoint.parse("0x6000:01");
        assertEquals("0x6000:01", object.wireToken());
        assertEquals(EthercatPoint.Kind.OBJECT, object.kind());
        assertEquals(0x6000, object.ado());
    }

    @Test
    void readAndWriteLogicalDatagramPoints() throws Exception {
        peer = new FakeEthercatPeer();
        peer.put(0x1000, 1);
        peer.put(0x2000, 12);
        peer.put(0x6000, 21);
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        TestDriverObject object = new TestDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new EthercatDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "s1", "slave:1",
                "pdo", "slave:1:pdo:0",
                "obj", "0x6000:01"
        ));
        assertEquals(1.0, (Double) object.variables.get("s1").firstRow().get("value"), 0.001);
        assertEquals(12.0, (Double) object.variables.get("pdo").firstRow().get("value"), 0.001);
        assertEquals(21.0, (Double) object.variables.get("obj").firstRow().get("value"), 0.001);

        driver.writePoint("pdo", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.0)
        ));
        assertEquals(33, peer.get(0x2000));
        assertEquals(33.0, (Double) object.variables.get("pdo").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new EthercatDeviceDriver();
        driver.initialize(new TestDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slave:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeEthercatPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ethercat");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeEthercatPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int ado, int value) {
            values.put(ado, value & 0xFFFF);
        }

        int get(int ado) {
            return values.getOrDefault(ado, 0);
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
                    byte[] header = readFully(in, 10);
                    int lengthField = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
                    int dataLen = lengthField & 0x7FF;
                    byte[] dataAndWkc = readFully(in, dataLen + 2);
                    byte[] frame = new byte[10 + dataAndWkc.length];
                    System.arraycopy(header, 0, frame, 0, 10);
                    System.arraycopy(dataAndWkc, 0, frame, 10, dataAndWkc.length);
                    out.write(respond(frame));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private byte[] respond(byte[] request) {
            EthercatCodec.ParsedDatagram parsed = EthercatCodec.parse(request);
            int[] data = new int[parsed.dataLength()];
            if (parsed.cmd() == EthercatCodec.CMD_LRD) {
                int value = values.getOrDefault(parsed.ado(), 0);
                int[] words = EthercatCodec.uint16LeBytes(value);
                for (int i = 0; i < data.length && i < words.length; i++) {
                    data[i] = words[i];
                }
            } else if (parsed.cmd() == EthercatCodec.CMD_LWR) {
                values.put(parsed.ado(), EthercatCodec.readUint16Le(parsed.data()));
                System.arraycopy(parsed.data(), 0, data, 0, Math.min(data.length, parsed.data().length));
            } else {
                System.arraycopy(parsed.data(), 0, data, 0, Math.min(data.length, parsed.data().length));
            }
            return EthercatCodec.buildDatagram(
                    parsed.cmd(), parsed.idx(), parsed.adp(), parsed.ado(),
                    false, parsed.irq(), data, 1);
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buf = new byte[length];
            int off = 0;
            while (off < length) {
                int n = in.read(buf, off, length - off);
                if (n < 0) {
                    throw new EOFException();
                }
                off += n;
            }
            return buf;
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
                    "test-ethercat",
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
