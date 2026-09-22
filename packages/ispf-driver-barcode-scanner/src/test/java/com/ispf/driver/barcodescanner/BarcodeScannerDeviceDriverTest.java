package com.ispf.driver.barcodescanner;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
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

class BarcodeScannerDeviceDriverTest {

    /** Handwritten ABC123 CR LF wire bytes (41 42 43 31 32 33 0D 0A). */
    private static final byte[] SCAN_LINE_LITERAL = new byte[] {
            0x41, 0x42, 0x43, 0x31, 0x32, 0x33, 0x0D, 0x0A
    };

    private BarcodeScannerDeviceDriver driver;
    private FakeScanner scanner;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
    }

    @Test
    void scanLineLiteralIsAbc123CrLf() throws IOException {
        assertArrayEquals(new byte[] {
                0x41, 0x42, 0x43, 0x31, 0x32, 0x33, 0x0D, 0x0A
        }, SCAN_LINE_LITERAL);
        assertEquals("ABC123", BarcodeScannerDeviceDriver.readLine(
                new ByteArrayInputStream(SCAN_LINE_LITERAL)));
    }

    @Test
    void metadataIsProductionReadWriteWithoutLab() {
        driver = new BarcodeScannerDeviceDriver();
        assertEquals("barcode-scanner", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("newline-terminated") || description.contains("scan stream"));
        assertFalse(description.contains("lab"));
        assertFalse(description.contains("trigger"));
        assertFalse(description.contains("beep"));
    }

    @Test
    void scanAndWriteLoopback() throws Exception {
        scanner = new FakeScanner();
        scanner.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(scanner.port()),
                "timeoutMs", "2000"
        ));
        driver = new BarcodeScannerDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("code", "last"));
        scanner.emit(SCAN_LINE_LITERAL);
        awaitScan(object, "code", "ABC123");

        driver.writePoint("code", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ACK")
        ));
        assertTrue(awaitCondition(() -> "ACK".equals(scanner.lastLine()), 2000));
        assertArrayEquals(
                "ACK\r\n".getBytes(StandardCharsets.US_ASCII),
                scanner.lastRawWrite());
    }

    private static void awaitScan(StubDriverObject object, String point, String expected) throws InterruptedException {
        assertTrue(awaitCondition(() -> {
            DataRecord record = object.variables.get(point);
            return record != null && expected.equals(record.firstRow().get("value"));
        }, 2000));
    }

    private static boolean awaitCondition(Check check, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return true;
            }
            Thread.sleep(20);
        }
        return check.ok();
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static final class FakeScanner implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-barcode-scanner");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<OutputStream> clientOut = new AtomicReference<>();
        private final AtomicReference<String> lastLine = new AtomicReference<>("");
        private final AtomicReference<byte[]> lastRawWrite = new AtomicReference<>(new byte[0]);

        FakeScanner() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String lastLine() {
            return lastLine.get();
        }

        byte[] lastRawWrite() {
            return lastRawWrite.get();
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        void emit(byte[] frame) throws IOException, InterruptedException {
            long deadline = System.currentTimeMillis() + 2000;
            while (clientOut.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            OutputStream out = clientOut.get();
            if (out == null) {
                throw new IOException("no client connected");
            }
            synchronized (out) {
                out.write(frame);
                out.flush();
            }
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
                clientOut.set(socket.getOutputStream());
                InputStream in = socket.getInputStream();
                while (true) {
                    byte[] raw = readRawLine(in);
                    if (raw == null) {
                        break;
                    }
                    lastRawWrite.set(raw);
                    String line = new String(raw, StandardCharsets.US_ASCII);
                    if (line.endsWith("\r\n")) {
                        line = line.substring(0, line.length() - 2);
                    } else if (line.endsWith("\n")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    lastLine.set(line.trim());
                }
            } catch (IOException ignored) {
                // client closed
            } finally {
                clientOut.set(null);
            }
        }

        private static byte[] readRawLine(InputStream in) throws IOException {
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
            return new PlatformObject("test-barcode", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
