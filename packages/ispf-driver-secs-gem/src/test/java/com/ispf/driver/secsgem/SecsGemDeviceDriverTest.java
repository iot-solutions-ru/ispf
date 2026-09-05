package com.ispf.driver.secsgem;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.secsgem.codec.HsmsLabCodec;
import com.ispf.driver.secsgem.codec.Secs2LabCodec;
import com.ispf.driver.secsgem.codec.SecsGemLabTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the HSMS/GEM-lab codec.
 */
class SecsGemDeviceDriverTest {

    private SecsGemDeviceDriver driver;
    private FakeHsmsLabServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataDescribesLabNotStub() {
        driver = new SecsGemDeviceDriver();
        assertEquals("secs-gem", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("hsms-lab"));
        assertTrue(driver.metadata().description().contains("S1F1"));
    }

    @Test
    void pointParserAcceptsS1F1StatusAndVid() {
        assertEquals(SecsGemPoint.Kind.S1F1, SecsGemPoint.parse("S1F1").kind());
        assertEquals(SecsGemPoint.Kind.STATUS, SecsGemPoint.parse("status").kind());
        assertEquals(new SecsGemPoint(SecsGemPoint.Kind.VID, 100L), SecsGemPoint.parse("VID:100"));
        assertEquals(new SecsGemPoint(SecsGemPoint.Kind.VID, 42L), SecsGemPoint.parse("42"));
    }

    @Test
    void connectSelectAndReadS1F1VidAndStatus() throws Exception {
        server = new FakeHsmsLabServer();
        server.putVid(100L, 3.5);
        server.setStatus("IDLE");
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "sessionId", "0",
                "timeoutMs", "2000"
        ));
        driver = new SecsGemDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "online", "S1F1",
                "chamber", "VID:100",
                "state", "status"
        ));

        DataRecord online = object.variables.get("online");
        assertEquals("true", online.firstRow().get("value"));
        assertEquals("ISPF-LAB", online.firstRow().get("mdln"));
        assertEquals("0.1.0", online.firstRow().get("softrev"));

        DataRecord chamber = object.variables.get("chamber");
        assertEquals(3.5, ((Number) chamber.firstRow().get("value")).doubleValue(), 0.001);
        assertEquals(100L, ((Number) chamber.firstRow().get("vid")).longValue());

        DataRecord state = object.variables.get("state");
        assertEquals("IDLE", state.firstRow().get("value"));
    }

    @Test
    void writeS2F41RemoteCommand() throws Exception {
        server = new FakeHsmsLabServer();
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new SecsGemDeviceDriver();
        driver.initialize(object);
        driver.connect();

        DataRecord command = DataRecord.single(
                DataSchema.builder("cmd").field("value", FieldType.STRING).build(),
                Map.of("value", "START")
        );
        driver.writePoint("rcmd", command);
        assertEquals("START", server.lastRcmd());
        assertEquals("START", object.variables.get("rcmd").firstRow().get("value"));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new SecsGemDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class,
                () -> driver.readPoints(Map.of("x", "S1F1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * Minimal HSMS equipment: Select.rsp + S1F14 + S1F2 + S2F14 + S6F2 + S2F42.
     */
    private static final class FakeHsmsLabServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-hsms-lab");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Long, Double> vids = new ConcurrentHashMap<>();
        private final AtomicReference<String> status = new AtomicReference<>("READY");
        private final AtomicReference<String> lastRcmd = new AtomicReference<>();

        FakeHsmsLabServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putVid(long vid, double value) {
            vids.put(vid, value);
        }

        void setStatus(String value) {
            status.set(value);
        }

        String lastRcmd() {
            return lastRcmd.get();
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
                while (!socket.isClosed()) {
                    HsmsLabCodec.HsmsMessage msg = readMessage(in);
                    if (msg.sType() == SecsGemLabTypes.STYPE_SELECT_REQ) {
                        out.write(HsmsLabCodec.encodeControl(
                                msg.sessionId(), msg.systemBytes(), SecsGemLabTypes.STYPE_SELECT_RSP));
                        out.flush();
                        continue;
                    }
                    if (msg.sType() == SecsGemLabTypes.STYPE_LINKTEST_REQ) {
                        out.write(HsmsLabCodec.encodeControl(
                                msg.sessionId(), msg.systemBytes(), SecsGemLabTypes.STYPE_LINKTEST_RSP));
                        out.flush();
                        continue;
                    }
                    if (msg.sType() == SecsGemLabTypes.STYPE_SEPARATE_REQ) {
                        return;
                    }
                    if (!msg.isData()) {
                        continue;
                    }
                    if (msg.stream() == 1 && msg.function() == SecsGemLabTypes.S1F13) {
                        byte[] body = Secs2LabCodec.encodeList(List.of(
                                Secs2LabCodec.encodeU1(0),
                                Secs2LabCodec.encodeList(List.of(
                                        Secs2LabCodec.encodeAscii("ISPF-LAB"),
                                        Secs2LabCodec.encodeAscii("0.1.0")
                                ))
                        ));
                        out.write(HsmsLabCodec.encodeData(
                                msg.sessionId(), 1, SecsGemLabTypes.S1F14, false, msg.systemBytes(), body));
                        out.flush();
                    } else if (msg.stream() == 1 && msg.function() == SecsGemLabTypes.S1F1) {
                        byte[] body = Secs2LabCodec.encodeList(List.of(
                                Secs2LabCodec.encodeAscii("ISPF-LAB"),
                                Secs2LabCodec.encodeAscii("0.1.0")
                        ));
                        out.write(HsmsLabCodec.encodeData(
                                msg.sessionId(), 1, SecsGemLabTypes.S1F2, false, msg.systemBytes(), body));
                        out.flush();
                    } else if (msg.stream() == 2 && msg.function() == SecsGemLabTypes.S2F13) {
                        Secs2LabCodec.Item root = Secs2LabCodec.parse(msg.body());
                        List<byte[]> values = new ArrayList<>();
                        for (Secs2LabCodec.Item child : root.children()) {
                            long vid = child.unsigned();
                            double value = vids.getOrDefault(vid, 0.0);
                            values.add(Secs2LabCodec.encodeF4((float) value));
                        }
                        out.write(HsmsLabCodec.encodeData(
                                msg.sessionId(), 2, SecsGemLabTypes.S2F14, false, msg.systemBytes(),
                                Secs2LabCodec.encodeList(values)));
                        out.flush();
                    } else if (msg.stream() == 6 && msg.function() == SecsGemLabTypes.S6F1) {
                        byte[] body = Secs2LabCodec.encodeAscii(status.get());
                        out.write(HsmsLabCodec.encodeData(
                                msg.sessionId(), 6, 2, false, msg.systemBytes(), body));
                        out.flush();
                    } else if (msg.stream() == 2 && msg.function() == SecsGemLabTypes.S2F41) {
                        Secs2LabCodec.Item root = Secs2LabCodec.parse(msg.body());
                        String rcmd = "";
                        if (root.isList() && !root.children().isEmpty()) {
                            rcmd = root.children().get(0).ascii() == null
                                    ? ""
                                    : root.children().get(0).ascii();
                        }
                        lastRcmd.set(rcmd);
                        byte[] body = Secs2LabCodec.encodeList(List.of(
                                Secs2LabCodec.encodeU1(0),
                                Secs2LabCodec.encodeEmptyList()
                        ));
                        out.write(HsmsLabCodec.encodeData(
                                msg.sessionId(), 2, 42, false, msg.systemBytes(), body));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static HsmsLabCodec.HsmsMessage readMessage(InputStream in) throws IOException {
            byte[] lengthBytes = in.readNBytes(4);
            if (lengthBytes.length != 4) {
                throw new EOFException();
            }
            int length = ByteBuffer.wrap(lengthBytes).getInt();
            byte[] rest = in.readNBytes(length);
            if (rest.length != length) {
                throw new EOFException();
            }
            byte[] frame = new byte[4 + length];
            System.arraycopy(lengthBytes, 0, frame, 0, 4);
            System.arraycopy(rest, 0, frame, 4, length);
            return HsmsLabCodec.parse(frame);
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
                    "test-secs-gem",
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
