package com.ispf.driver.zwave;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.zwave.codec.ZwaveSerialApiCodec;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process Serial API over TCP peer tests for the Z-Wave driver.
 */
class ZwaveDeviceDriverTest {

    private ZwaveDeviceDriver driver;
    private FakeZwaveSerialApiPeer peer;

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
    void serialApiRequestLiteralsAreHandwritten() {
        // Handwritten octets — not produced by calling the encoder for the expected array.
        byte[] getVersion = new byte[] {
                0x01, 0x03, 0x00, 0x15, (byte) 0xE9
        };
        byte[] nodeInfoNode1 = new byte[] {
                0x01, 0x04, 0x00, 0x41, 0x01, (byte) 0xBB
        };

        assertArrayEquals(getVersion, ZwaveDeviceDriver.getVersionRequestLiteral());
        assertArrayEquals(getVersion, ZwaveSerialApiCodec.getVersionRequestLiteral());
        assertArrayEquals(getVersion,
                ZwaveSerialApiCodec.encodeRequest(ZwaveSerialApiCodec.FUNC_ID_ZW_GET_VERSION));

        assertArrayEquals(nodeInfoNode1, ZwaveDeviceDriver.getNodeProtocolInfoNode1RequestLiteral());
        assertArrayEquals(nodeInfoNode1, ZwaveSerialApiCodec.getNodeProtocolInfoRequestLiteral(1));
        assertArrayEquals(nodeInfoNode1,
                ZwaveSerialApiCodec.encodeRequest(
                        ZwaveSerialApiCodec.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO, (byte) 0x01));
    }

    @Test
    void metadataIsProductionSerialApiOverTcp() {
        driver = new ZwaveDeviceDriver();
        assertEquals("zwave", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("3000", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("serial api over tcp"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsNodeAndCmdForms() throws Exception {
        assertEquals(3, ZwavePoint.parse("node:3").node());
        assertEquals(ZwavePoint.Kind.NODE, ZwavePoint.parse("node:3").kind());
        ZwavePoint cmd = ZwavePoint.parse("node:3:cmd:37");
        assertEquals(ZwavePoint.Kind.CMD, cmd.kind());
        assertEquals(3, cmd.node());
        assertEquals(37, cmd.commandClass());
    }

    @Test
    void readNodeAndCmdWriteLoopback() throws Exception {
        peer = new FakeZwaveSerialApiPeer();
        peer.putCapability(3, 0x01);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ZwaveDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "n3", "node:3",
                "sw", "node:3:cmd:37"
        ));
        assertEquals(1.0, (Double) object.variables.get("n3").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("sw").firstRow().get("value"), 0.001);

        driver.writePoint("sw", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        assertEquals(1.0, (Double) object.variables.get("sw").firstRow().get("value"), 0.001);

        driver.writePoint("n3", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 0.0)
        ));
        assertEquals(0.0, (Double) object.variables.get("n3").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ZwaveDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "node:3")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * In-process Serial API over TCP peer.
     * GET_VERSION → ACK then a short Serial API response; node-info → ACK (then short response).
     */
    private static final class FakeZwaveSerialApiPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-zwave-serial-api");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> capabilities = new ConcurrentHashMap<>();

        FakeZwaveSerialApiPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putCapability(int nodeId, int capability) {
            capabilities.put(nodeId, capability & 0xFF);
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
                    byte[] request = ZwaveSerialApiCodec.readFrame(in);
                    int func = ZwaveSerialApiCodec.functionId(request);
                    ZwaveSerialApiCodec.writeAck(out);
                    if (func == ZwaveSerialApiCodec.FUNC_ID_ZW_GET_VERSION) {
                        // Short Serial API response after ACK.
                        ZwaveSerialApiCodec.writeFrame(out, ZwaveSerialApiCodec.encodeResponse(
                                ZwaveSerialApiCodec.FUNC_ID_ZW_GET_VERSION,
                                (byte) 0x01, (byte) 0x00));
                        ZwaveSerialApiCodec.readAck(in);
                    } else if (func == ZwaveSerialApiCodec.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO) {
                        byte[] params = ZwaveSerialApiCodec.params(request);
                        int nodeId = params.length > 0 ? params[0] & 0xFF : 1;
                        int capability = capabilities.getOrDefault(nodeId, 0);
                        ZwaveSerialApiCodec.writeFrame(out, ZwaveSerialApiCodec.encodeResponse(
                                ZwaveSerialApiCodec.FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO,
                                (byte) capability, (byte) 0x00, (byte) 0x00,
                                (byte) 0x01, (byte) 0x10, (byte) 0x01));
                        ZwaveSerialApiCodec.readAck(in);
                    }
                }
            } catch (IOException ignored) {
                // client closed
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

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-zwave",
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
