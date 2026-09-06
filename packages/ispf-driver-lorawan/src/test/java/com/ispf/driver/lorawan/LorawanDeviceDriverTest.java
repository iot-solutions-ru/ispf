package com.ispf.driver.lorawan;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP NS/AS loopback tests for the LoRaWAN gateway lab.
 * Certifies lab dialect only — not LoRa PHY / Semtech HAL.
 */
class LorawanDeviceDriverTest {

    private LorawanDeviceDriver driver;
    private FakeLorawanNs gateway;

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
    void metadataDescribesNsAsGatewayLabNotLoRaPhy() {
        driver = new LorawanDeviceDriver();
        assertEquals("lorawan", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("1700", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab") || description.contains("gateway"));
        assertTrue(description.contains("not lora phy") || description.contains("semtech hal"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsDevEuiForms() throws Exception {
        assertEquals("AABBCCDDEEFF0011", LorawanPoint.parse("AABBCCDDEEFF0011").deveui());
        assertEquals("AABBCCDDEEFF0011", LorawanPoint.parse("deveui:AABBCCDDEEFF0011").deveui());
    }

    @Test
    void getUplinkAndTxDownlink() throws Exception {
        gateway = new FakeLorawanNs();
        gateway.put("AABBCCDDEEFF0011", 21.5f, -87.0);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new LorawanDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("dev", "deveui:AABBCCDDEEFF0011"));
        assertEquals(21.5, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);
        assertEquals("AABBCCDDEEFF0011", object.variables.get("dev").firstRow().get("deveui"));
        assertEquals(-87.0, (Double) object.variables.get("dev").firstRow().get("rssi"), 0.001);

        driver.writePoint("dev", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        ));
        assertEquals(42.0f, gateway.get("AABBCCDDEEFF0011"), 0.001f);
        assertTrue(gateway.writeLatchAwait(2, TimeUnit.SECONDS));
    }

    private static final class FakeLorawanNs implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-lorawan");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final Map<String, Double> rssi = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeLorawanNs() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String deveui, float value, double rssiDb) {
            values.put(deveui.toUpperCase(Locale.ROOT), value);
            rssi.put(deveui.toUpperCase(Locale.ROOT), rssiDb);
        }

        float get(String deveui) {
            return values.getOrDefault(deveui.toUpperCase(Locale.ROOT), 0f);
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
                    String line = readLine(in);
                    if (line == null) {
                        return;
                    }
                    String upper = line.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("GET ")) {
                        String deveui = upper.substring(4).trim();
                        float value = values.getOrDefault(deveui, 0f);
                        double r = rssi.getOrDefault(deveui, -90.0);
                        String json = "{\"deveui\":\"" + deveui + "\",\"value\":" + value
                                + ",\"rssi\":" + r + "}";
                        writeLine(out, json);
                    } else if (upper.startsWith("TX ")) {
                        String[] parts = upper.substring(3).trim().split("\\s+");
                        String deveui = parts[0];
                        float value = parts.length > 1 ? Float.parseFloat(parts[1]) : 0f;
                        values.put(deveui, value);
                        writeSeen.countDown();
                        writeLine(out, "{\"ok\":true,\"deveui\":\"" + deveui + "\",\"value\":" + value + "}");
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
            return new PlatformObject("test-lorawan", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
