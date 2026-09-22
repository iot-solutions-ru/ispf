package com.ispf.driver.dali;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.dali.codec.DaliCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaliDeviceDriverTest {

    private DaliDeviceDriver driver;
    private FakeDaliPeer peer;

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
    void offToShortAddress0IsExactly0100() {
        byte[] expected = new byte[] { 0x01, 0x00 };
        assertArrayEquals(expected, DaliCodec.buildOffShortAddress0());
        assertArrayEquals(expected, DaliDeviceDriver.buildOffShortAddress0Frame());
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new DaliDeviceDriver();
        assertEquals("dali", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("62386") || description.contains("dali"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void queryAndSetLoopback() throws Exception {
        peer = new FakeDaliPeer();
        peer.setLevel(5, 120);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new DaliDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("lamp", "A5"));
        assertEquals("120", object.variables.get("lamp").firstRow().get("value"));

        driver.writePoint("lamp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "200")
        ));
        assertEquals(200, peer.level(5));
        driver.readPoints(Map.of("lamp", "A5"));
        assertEquals("200", object.variables.get("lamp").firstRow().get("value"));
    }

    private static final class FakeDaliPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-dali");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Integer> levels = new ConcurrentHashMap<>();

        FakeDaliPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setLevel(int shortAddress, int level) {
            levels.put(shortAddress, level);
        }

        int level(int shortAddress) {
            return levels.getOrDefault(shortAddress, 0);
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
                    int address = in.read();
                    if (address < 0) {
                        return;
                    }
                    int data = in.read();
                    if (data < 0) {
                        throw new EOFException();
                    }
                    boolean command = (address & 1) != 0;
                    int shortAddress = (address >> 1) & 0x3F;
                    if (command) {
                        if ((data & 0xFF) == DaliCodec.CMD_QUERY_ACTUAL_LEVEL) {
                            out.write(levels.getOrDefault(shortAddress, 0) & 0xFF);
                            out.flush();
                        } else if ((data & 0xFF) == DaliCodec.CMD_OFF) {
                            levels.put(shortAddress, 0);
                            out.write(0);
                            out.flush();
                        } else {
                            out.write(0xFF);
                            out.flush();
                        }
                    } else {
                        levels.put(shortAddress, data & 0xFF);
                        out.write(data & 0xFF);
                        out.flush();
                    }
                }
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-dali", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
