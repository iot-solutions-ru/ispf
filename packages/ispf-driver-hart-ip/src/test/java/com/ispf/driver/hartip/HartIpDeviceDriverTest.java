package com.ispf.driver.hartip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.hartip.codec.HartIpLabCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the HART-IP lab codec.
 */
class HartIpDeviceDriverTest {

    private HartIpDeviceDriver driver;
    private FakeHartIpServer server;

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
        driver = new HartIpDeviceDriver();
        assertEquals("hart-ip", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("lab"));
        assertTrue(driver.metadata().description().toLowerCase().contains("not full"));
    }

    @Test
    void pointParserAcceptsPvCmdAndDeviceForms() throws Exception {
        assertEquals(new HartIpPoint(0, 1), HartIpPoint.parse("pv"));
        assertEquals(new HartIpPoint(0, 1), HartIpPoint.parse("cmd:1"));
        assertEquals(new HartIpPoint(0, 3), HartIpPoint.parse("cmd:3"));
        assertEquals(new HartIpPoint(0, 1), HartIpPoint.parse("device:0"));
        assertEquals(new HartIpPoint(2, 1), HartIpPoint.parse("device:2:cmd:1"));
        assertEquals(new HartIpPoint(0, 1), HartIpPoint.parse("0:1"));
    }

    @Test
    void sessionAndPassThroughReadPv() throws Exception {
        server = new FakeHartIpServer();
        server.put(0, 21.5f);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new HartIpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pv", "pv",
                "cmd1", "cmd:1",
                "dev0", "device:0"
        ));
        assertEquals(21.5, (Double) object.variables.get("pv").firstRow().get("value"), 0.001);
        assertEquals(21.5, (Double) object.variables.get("cmd1").firstRow().get("value"), 0.001);
        assertEquals(21.5, (Double) object.variables.get("dev0").firstRow().get("value"), 0.001);
        assertEquals(1L, object.variables.get("pv").firstRow().get("command"));
    }

    @Test
    void command3ReadsDynamicStylePv() throws Exception {
        server = new FakeHartIpServer();
        server.put(0, 33.25f);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new HartIpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("dyn", "cmd:3"));
        assertEquals(33.25, (Double) object.variables.get("dyn").firstRow().get("value"), 0.001);
        assertEquals(3L, object.variables.get("dyn").firstRow().get("command"));
    }

    @Test
    void writeIsRejected() {
        driver = new HartIpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("pv", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "x")
                )));
        assertTrue(error.getMessage().toLowerCase().contains("read-only"));
    }

    private static final class FakeHartIpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-hart-ip");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();

        FakeHartIpServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int device, float pv) {
            values.put(device, pv);
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
                    byte[] frame = readFrame(in);
                    HartIpLabCodec.HartIpMessage message = HartIpLabCodec.decode(frame);
                    if (message.messageId() == HartIpLabCodec.ID_SESSION_INITIATE) {
                        out.write(HartIpLabCodec.encodeSessionInitiateResponse(message.sequence()));
                        out.flush();
                        continue;
                    }
                    if (message.messageId() == HartIpLabCodec.ID_PASS_THROUGH) {
                        HartIpLabCodec.HartCommand command = HartIpLabCodec.parseHartCommand(message.payload());
                        float pv = values.getOrDefault(command.address(), 0f);
                        byte[] hart = HartIpLabCodec.encodeHartPvResponse(
                                command.address(), command.command(), pv);
                        out.write(HartIpLabCodec.encodePassThroughResponse(message.sequence(), hart));
                        out.flush();
                        continue;
                    }
                    out.write(HartIpLabCodec.encodeMessage(
                            HartIpLabCodec.MSG_NAK, message.messageId(), 1, message.sequence(), new byte[0]));
                    out.flush();
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static byte[] readFrame(InputStream in) throws IOException {
            byte[] header = readFully(in, 10);
            int byteCount = ((header[8] & 0xFF) << 8) | (header[9] & 0xFF);
            byte[] payload = byteCount == 0 ? new byte[0] : readFully(in, byteCount);
            byte[] frame = new byte[10 + payload.length];
            System.arraycopy(header, 0, frame, 0, 10);
            System.arraycopy(payload, 0, frame, 10, payload.length);
            return frame;
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buffer = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(buffer, offset, length - offset);
                if (read < 0) {
                    throw new EOFException();
                }
                offset += read;
            }
            return buffer;
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
            return new PlatformObject("test-hart-ip", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
