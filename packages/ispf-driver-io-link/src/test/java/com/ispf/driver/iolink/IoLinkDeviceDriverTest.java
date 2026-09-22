package com.ispf.driver.iolink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iolink.codec.IoLinkCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoLinkDeviceDriverTest {

    private IoLinkDeviceDriver driver;
    private FakeIoLinkPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesIsduOverTcp() {
        driver = new IoLinkDeviceDriver();
        assertEquals("io-link", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("isdu"));
        assertTrue(description.contains("tcp"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsPortForms() throws Exception {
        assertEquals(1, IoLinkPoint.parse("port:1").port());
        assertEquals(IoLinkPoint.Channel.PDIN, IoLinkPoint.parse("port:1:pdin").channel());
        assertEquals(IoLinkPoint.Channel.PDOUT, IoLinkPoint.parse("port:1:pdout").channel());
    }

    @Test
    void readAndWriteIsdu() throws Exception {
        peer = new FakeIoLinkPeer();
        peer.put(1, 0x0010, 0, 42.0f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new IoLinkDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("p", "port:1"));
        assertEquals(42.0, (Double) object.variables.get("p").firstRow().get("value"), 0.001);

        driver.writePoint("p", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 11.0)
        ));
        assertEquals(11.0f, peer.get(1, 0x0010, 0), 0.001f);
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new IoLinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "port:1")));
    }

    private static final class FakeIoLinkPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-iolink");
            t.setDaemon(true);
            return t;
        });
        private final Map<Long, Float> values = new ConcurrentHashMap<>();

        FakeIoLinkPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int port, int index, int sub, float v) { values.put(key(port, index, sub), v); }
        float get(int port, int index, int sub) { return values.getOrDefault(key(port, index, sub), 0f); }
        void start() { executor.submit(this::acceptLoop); }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                socket.setSoTimeout(2000);
                while (true) {
                    byte[] header = IoLinkCodec.readFully(in, 4);
                    int port = header[0] & 0xFF;
                    int index = ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
                    int sub = header[3] & 0xFF;
                    socket.setSoTimeout(30);
                    try {
                        byte[] data = IoLinkCodec.readFully(in, 4);
                        values.put(key(port, index, sub), ByteBuffer.wrap(data).getFloat());
                        out.write(0x00);
                        out.flush();
                    } catch (java.net.SocketTimeoutException readOnly) {
                        out.write(IoLinkCodec.encodeFloat(values.getOrDefault(key(port, index, sub), 0f)));
                        out.flush();
                    } finally {
                        socket.setSoTimeout(2000);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static long key(int port, int index, int sub) {
            return (((long) port) << 24) | (((long) index) << 8) | (sub & 0xFF);
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
        StubDriverObject(Map<String, String> configuration) { this.configuration = configuration; }
        @Override public PlatformObject deviceObject() {
            return new PlatformObject("t", "root.platform.devices.t", ObjectType.DEVICE, "T", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) { }
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
