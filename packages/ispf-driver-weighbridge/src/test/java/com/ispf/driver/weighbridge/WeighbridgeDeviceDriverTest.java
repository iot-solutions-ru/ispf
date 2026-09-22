package com.ispf.driver.weighbridge;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeighbridgeDeviceDriverTest {

    /** Handwritten MT-SICS S CR LF (53 0D 0A). */
    private static final byte[] CMD_S = new byte[] { 0x53, 0x0D, 0x0A };
    /** Handwritten MT-SICS Z CR LF (5A 0D 0A). */
    private static final byte[] CMD_Z = new byte[] { 0x5A, 0x0D, 0x0A };
    /** Handwritten MT-SICS T CR LF (54 0D 0A). */
    private static final byte[] CMD_T = new byte[] { 0x54, 0x0D, 0x0A };

    /** Exact stable-weight reply line written for the peer (not built by the encoder). */
    private static final String STABLE_REPLY = "S S      0.00 kg\r\n";

    private WeighbridgeDeviceDriver driver;
    private FakeScale scale;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (scale != null) {
            scale.close();
            scale = null;
        }
    }

    @Test
    void mtSicsCommandOctetsAreHandwritten() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WeighbridgeDeviceDriver.writeLine(out, "S");
        assertArrayEquals(new byte[] { 0x53, 0x0D, 0x0A }, out.toByteArray());

        out.reset();
        WeighbridgeDeviceDriver.writeLine(out, "Z");
        assertArrayEquals(new byte[] { 0x5A, 0x0D, 0x0A }, out.toByteArray());

        out.reset();
        WeighbridgeDeviceDriver.writeLine(out, "T");
        assertArrayEquals(new byte[] { 0x54, 0x0D, 0x0A }, out.toByteArray());
    }

    @Test
    void metadataIsProductionReadWriteMtSics() {
        driver = new WeighbridgeDeviceDriver();
        assertEquals("weighbridge", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description();
        String lower = description.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("mt-sics"));
        assertTrue(lower.contains("not every truck-scale dialect"));
        assertFalse(lower.contains("lab"));
    }

    @Test
    void pollWeightZeroAndTareLoopback() throws Exception {
        scale = new FakeScale();
        scale.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(scale.port()),
                "timeoutMs", "2000"
        ));
        driver = new WeighbridgeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("gross", "weight"));
        assertArrayEquals(CMD_S, scale.lastCommandBytes());
        assertEquals("0.00", object.variables.get("gross").firstRow().get("value"));
        assertEquals("kg", object.variables.get("gross").firstRow().get("unit"));
        assertEquals("S", object.variables.get("gross").firstRow().get("status"));

        driver.writePoint("gross", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ZERO")
        ));
        assertArrayEquals(CMD_Z, scale.lastCommandBytes());
        assertEquals("Z", scale.lastCommand());

        driver.writePoint("gross", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "TARE")
        ));
        assertArrayEquals(CMD_T, scale.lastCommandBytes());
        assertEquals("T", scale.lastCommand());

        driver.readPoints(Map.of("gross", "weight"));
        assertArrayEquals(CMD_S, scale.lastCommandBytes());
        assertEquals("0.00", object.variables.get("gross").firstRow().get("value"));
        assertEquals("kg", object.variables.get("gross").firstRow().get("unit"));
    }

    @Test
    void parseMtSicsStableWeightLine() {
        WeighbridgeDeviceDriver.ParsedWeight parsed =
                WeighbridgeDeviceDriver.parseWeight("S S      0.00 kg");
        assertEquals("0.00", parsed.value());
        assertEquals("kg", parsed.unit());
        assertEquals("S", parsed.status());
    }

    @Test
    void parseSignedWeightLine() {
        WeighbridgeDeviceDriver.ParsedWeight parsed =
                WeighbridgeDeviceDriver.parseWeight("ST,GS,+000256.7kg");
        assertEquals("000256.7", parsed.value());
        assertEquals("kg", parsed.unit());
        assertEquals("ST", parsed.status());
    }

    private static final class FakeScale implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-weighbridge");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<String> lastCommand = new AtomicReference<>("");
        private final AtomicReference<byte[]> lastCommandBytes = new AtomicReference<>(new byte[0]);

        FakeScale() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String lastCommand() {
            return lastCommand.get();
        }

        byte[] lastCommandBytes() {
            return lastCommandBytes.get();
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    byte[] frame = readFrame(in);
                    if (frame == null) {
                        break;
                    }
                    lastCommandBytes.set(frame);
                    String command = new String(frame, StandardCharsets.US_ASCII)
                            .replace("\r", "")
                            .replace("\n", "")
                            .trim();
                    String upper = command.toUpperCase(Locale.ROOT);
                    lastCommand.set(upper);
                    if ("S".equals(upper)) {
                        out.write(STABLE_REPLY.getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else if ("Z".equals(upper) || "T".equals(upper)) {
                        out.write("OK\r\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else {
                        out.write("ES\r\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static byte[] readFrame(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    if (buf.size() == 0) {
                        return null;
                    }
                    break;
                }
                buf.write(b);
                if (b == '\n') {
                    break;
                }
            }
            return buf.toByteArray();
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
            return new PlatformObject("test-wb", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
