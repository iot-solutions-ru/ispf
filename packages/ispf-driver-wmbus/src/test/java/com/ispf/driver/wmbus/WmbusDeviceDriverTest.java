package com.ispf.driver.wmbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.wmbus.codec.WmbusLabCodec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP gateway loopback tests for the Wireless M-Bus lab codec.
 */
class WmbusDeviceDriverTest {

    private WmbusDeviceDriver driver;
    private FakeWmbusGateway gateway;

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
    void metadataDescribesTcpGatewayLabNotRfPhy() {
        driver = new WmbusDeviceDriver();
        assertEquals("wmbus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab") || description.contains("gateway"));
        assertTrue(description.contains("not rf") || description.contains("not an rf"));
    }

    @Test
    void pointParserAcceptsMeterAndIdForms() throws Exception {
        assertEquals(new WmbusPoint(WmbusPoint.Kind.METER_INDEX, "1"), WmbusPoint.parse("meter:1"));
        assertEquals(new WmbusPoint(WmbusPoint.Kind.DEVICE_ID, "AABBCCDD"), WmbusPoint.parse("id:AABBCCDD"));
        assertEquals(new WmbusPoint(WmbusPoint.Kind.METER_INDEX, "2"), WmbusPoint.parse("2"));
    }

    @Test
    void pollMeterAndIdReturnsParsedCiValue() throws Exception {
        gateway = new FakeWmbusGateway();
        gateway.putMeter(1, WmbusLabCodec.encodeShortFrame(0x1B2C, 0xAABBCCDDL, 1, 7, 12.5f));
        gateway.putId("AABBCCDD", WmbusLabCodec.encodeShortFrame(0x1B2C, 0xAABBCCDDL, 1, 7, 12.5f));
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new WmbusDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "m1", "meter:1",
                "byId", "id:AABBCCDD"
        ));
        assertEquals(12.5, (Double) object.variables.get("m1").firstRow().get("value"), 0.001);
        assertEquals("AABBCCDD", object.variables.get("m1").firstRow().get("deviceId"));
        assertEquals(0x78L, object.variables.get("m1").firstRow().get("ci"));
        assertEquals(12.5, (Double) object.variables.get("byId").firstRow().get("value"), 0.001);
    }

    @Test
    void writeIsRejected() {
        driver = new WmbusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("m1", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "x")
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("read-only"));
    }

    private static final class FakeWmbusGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-wmbus");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, byte[]> byToken = new ConcurrentHashMap<>();

        FakeWmbusGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putMeter(int index, byte[] frame) {
            byToken.put("meter:" + index, frame);
        }

        void putId(String hexId, byte[] frame) {
            byToken.put("id:" + hexId.toUpperCase(Locale.ROOT), frame);
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
                    String command = readLine(in);
                    if (command == null) {
                        return;
                    }
                    String upper = command.trim();
                    if (upper.regionMatches(true, 0, "POLL ", 0, 5)) {
                        String token = upper.substring(5).trim().toLowerCase(Locale.ROOT);
                        if (token.startsWith("id:")) {
                            token = "id:" + token.substring(3).toUpperCase(Locale.ROOT);
                        }
                        byte[] frame = byToken.get(token);
                        if (frame == null) {
                            writeLine(out, "ERR unknown");
                        } else {
                            writeLine(out, "TELEGRAM " + WmbusLabCodec.toHex(frame));
                        }
                    } else {
                        writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static void writeLine(OutputStream out, String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    if (buf.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buf.write(b);
                }
            }
            return buf.toString(StandardCharsets.US_ASCII);
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
            return new PlatformObject("test-wmbus", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
