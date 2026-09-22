package com.ispf.driver.asinterface;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.asinterface.codec.AsInterfaceCodec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsInterfaceDeviceDriverTest {

    private AsInterfaceDeviceDriver driver;
    private FakeAsiPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesMasterCallGateway() {
        driver = new AsInterfaceDeviceDriver();
        assertEquals("as-interface", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("tcp") || description.contains("gateway"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsSlaveAndDiDoForms() throws Exception {
        assertEquals(3, AsInterfacePoint.parse("slave:3").slave());
        assertEquals(AsInterfacePoint.Channel.DI, AsInterfacePoint.parse("slave:3:di0").channel());
        assertEquals(AsInterfacePoint.Channel.DO, AsInterfacePoint.parse("slave:3:do1").channel());
    }

    @Test
    void readAndWriteMasterCall() throws Exception {
        peer = new FakeAsiPeer();
        peer.put(3, 5);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new AsInterfaceDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("s", "slave:3"));
        assertEquals(5.0, (Double) object.variables.get("s").firstRow().get("value"), 0.001);

        driver.writePoint("s", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 7)
        ));
        assertEquals(7, peer.get(3));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new AsInterfaceDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "slave:1")));
    }

    private static final class FakeAsiPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-asi");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Integer> values = new ConcurrentHashMap<>();

        FakeAsiPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int addr, int value) { values.put(addr, value); }
        int get(int addr) { return values.getOrDefault(addr, 0); }
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
                while (true) {
                    byte[] call = AsInterfaceCodec.readFully(in, 2);
                    int addr = call[0] & 0xFF;
                    int data = call[1] & 0xFF;
                    if ((data & 0x80) != 0) {
                        values.put(addr, data & 0x7F);
                        out.write(0x00);
                    } else {
                        out.write(values.getOrDefault(addr, 0) & 0xFF);
                    }
                    out.flush();
                }
            } catch (IOException ignored) {
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
