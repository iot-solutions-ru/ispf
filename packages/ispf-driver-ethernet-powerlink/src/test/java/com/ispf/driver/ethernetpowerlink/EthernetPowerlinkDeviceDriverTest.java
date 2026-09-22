package com.ispf.driver.ethernetpowerlink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.ethernetpowerlink.codec.EthernetPowerlinkCodec;
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

class EthernetPowerlinkDeviceDriverTest {

    private EthernetPowerlinkDeviceDriver driver;
    private FakePowerlinkPeer peer;

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
    void metadataDescribesPowerlinkTcpGateway() {
        driver = new EthernetPowerlinkDeviceDriver();
        assertEquals("ethernet-powerlink", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("powerlink"));
        assertTrue(description.contains("tcp"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsNodeAndPdoForms() throws Exception {
        assertEquals(1, EthernetPowerlinkPoint.parse("node:1:obj:0x6000:01").destinationNode());
        assertEquals(1, EthernetPowerlinkPoint.parse("pdo:1").destinationNode());
    }

    @Test
    void readAndWriteViaPreq() throws Exception {
        peer = new FakePowerlinkPeer();
        peer.put(1, 12.5f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new EthernetPowerlinkDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("n", "node:1:obj:0x6000:01"));
        assertEquals(12.5, (Double) object.variables.get("n").firstRow().get("value"), 0.001);

        driver.writePoint("n", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 9.5)
        ));
        assertEquals(9.5f, peer.get(1), 0.001f);
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new EthernetPowerlinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "pdo:1")));
    }

    private static final class FakePowerlinkPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-powerlink");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();

        FakePowerlinkPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int dest, float value) { values.put(dest, value); }
        float get(int dest) { return values.getOrDefault(dest, 0f); }
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
                    byte[] header = EthernetPowerlinkCodec.readFully(in, 3);
                    int dest = header[1] & 0xFF;
                    byte[] payload = EthernetPowerlinkCodec.readFully(in, 4);
                    boolean allZero = payload[0] == 0 && payload[1] == 0 && payload[2] == 0 && payload[3] == 0;
                    if (allZero) {
                        float value = values.getOrDefault(dest, 0f);
                        out.write(EthernetPowerlinkCodec.encodePRes(
                                EthernetPowerlinkCodec.MN_NODE, dest, EthernetPowerlinkCodec.encodeFloat(value)));
                        out.flush();
                    } else {
                        values.put(dest, ByteBuffer.wrap(payload).getFloat());
                        out.write(0x00);
                        out.flush();
                    }
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
