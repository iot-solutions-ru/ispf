package com.ispf.driver.wirelesshart;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.wirelesshart.codec.WirelesshartCodec;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake HART-IP gateway loopback tests for WirelessHART.
 * Certifies HART-IP framing — not 802.15.4 radio / HCF stack.
 */
class WirelesshartDeviceDriverTest {

    private WirelesshartDeviceDriver driver;
    private FakeWirelesshartGateway gateway;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }

    @Test
    void metadataDescribesGatewayHartIpNotRadioStack() {
        driver = new WirelesshartDeviceDriver();
        assertEquals("wirelesshart", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("5094", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertFalse(description.contains("lab"));
        assertTrue(description.contains("hart-ip") || description.contains("gateway"));
        assertTrue(description.contains("not") && (description.contains("802.15.4") || description.contains("hcf")));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void sessionInitiateSequence1IsLiteral() {
        assertArrayEquals(WirelesshartCodec.sessionInitiateSeq1(), WirelesshartCodec.encodeSessionInitiate(1));
        assertArrayEquals(WirelesshartCodec.hartCmd1Addr0(), WirelesshartCodec.encodeHartCommand(0, 1));
    }

    @Test
    void pointParserAcceptsPvCmdAndDeviceForms() throws Exception {
        assertEquals(new WirelesshartPoint(0, 1), WirelesshartPoint.parse("pv"));
        assertEquals(new WirelesshartPoint(0, 1), WirelesshartPoint.parse("cmd:1"));
        assertEquals(new WirelesshartPoint(0, 3), WirelesshartPoint.parse("cmd:3"));
        assertEquals(new WirelesshartPoint(0, 1), WirelesshartPoint.parse("device:0"));
        assertEquals(new WirelesshartPoint(2, 1), WirelesshartPoint.parse("device:2:cmd:1"));
        assertEquals(new WirelesshartPoint(0, 1), WirelesshartPoint.parse("0:1"));
    }

    @Test
    void getAndSetPvLoopback() throws Exception {
        gateway = new FakeWirelesshartGateway();
        gateway.put(0, 21.5f);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new WirelesshartDeviceDriver();
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

        driver.writePoint("pv", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25f, gateway.get(0), 0.001f);
        assertTrue(gateway.writeLatchAwait(2, TimeUnit.SECONDS));
    }

    private static final class FakeWirelesshartGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-wirelesshart");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeWirelesshartGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int device, float pv) {
            values.put(device, pv);
        }

        float get(int device) {
            return values.getOrDefault(device, 0f);
        }

        void start() {
            executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean writeLatchAwait(long timeout, TimeUnit unit) throws InterruptedException {
            return writeSeen.await(timeout, unit);
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
                    if (frame == null) {
                        return;
                    }
                    WirelesshartCodec.Message msg = WirelesshartCodec.decode(frame);
                    if (msg.messageId() == WirelesshartCodec.ID_SESSION_INITIATE) {
                        out.write(WirelesshartCodec.encodeSessionInitiateResponse(msg.sequence()));
                        out.flush();
                        continue;
                    }
                    if (msg.messageId() == WirelesshartCodec.ID_PASS_THROUGH) {
                        WirelesshartCodec.HartCommand cmd = WirelesshartCodec.parseHartCommand(msg.body());
                        if (cmd.byteCount() >= 4 && !Float.isNaN(cmd.writeValue())) {
                            values.put(cmd.address(), cmd.writeValue());
                            writeSeen.countDown();
                            byte[] ack = WirelesshartCodec.encodeHartPvResponse(
                                    cmd.address(), cmd.command(), cmd.writeValue());
                            out.write(WirelesshartCodec.encodePassThroughResponse(msg.sequence(), ack));
                            out.flush();
                        } else {
                            float pv = values.getOrDefault(cmd.address(), 0f);
                            byte[] response = WirelesshartCodec.encodeHartPvResponse(
                                    cmd.address(), cmd.command(), pv);
                            out.write(WirelesshartCodec.encodePassThroughResponse(msg.sequence(), response));
                            out.flush();
                        }
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static byte[] readFrame(InputStream in) throws IOException {
            byte[] header = new byte[WirelesshartCodec.HEADER_LENGTH];
            int offset = 0;
            while (offset < header.length) {
                int read = in.read(header, offset, header.length - offset);
                if (read < 0) {
                    if (offset == 0) {
                        return null;
                    }
                    throw new EOFException("EOF reading HART-IP header");
                }
                offset += read;
            }
            int byteCount = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            byte[] body = byteCount == 0 ? new byte[0] : new byte[byteCount];
            offset = 0;
            while (offset < body.length) {
                int read = in.read(body, offset, body.length - offset);
                if (read < 0) {
                    throw new EOFException("EOF reading HART-IP body");
                }
                offset += read;
            }
            byte[] frame = Arrays.copyOf(header, header.length + body.length);
            System.arraycopy(body, 0, frame, header.length, body.length);
            return frame;
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
            return new PlatformObject("test-wirelesshart", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
