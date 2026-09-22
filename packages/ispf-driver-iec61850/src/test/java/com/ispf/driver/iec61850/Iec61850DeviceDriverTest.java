package com.ispf.driver.iec61850;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iec61850.codec.Iec61850Codec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process TPKT/COTP peer tests for the IEC 61850 MMS client.
 */
class Iec61850DeviceDriverTest {

    private Iec61850DeviceDriver driver;
    private FakeMmsPeer peer;

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
    void metadataIsProductionReadWriteMmsTpkt() {
        driver = new Iec61850DeviceDriver();
        assertEquals("iec61850", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("102", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("mms"));
        assertTrue(description.contains("tpkt") || description.contains("cotp"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsObjectReferenceForms() throws Exception {
        assertEquals("LD0/MMXU1.TotW.mag.f",
                Iec61850Point.parse("LD0/MMXU1.TotW.mag.f").wireToken());
        assertEquals("IED1/LLN0.Mod.stVal",
                Iec61850Point.parse("IED1/LLN0.Mod.stVal").wireToken());
        assertEquals("analog", Iec61850Point.parse("LD0/MMXU1.TotW.mag.f").kind());
        assertEquals("status", Iec61850Point.parse("IED1/LLN0.Mod.stVal").kind());
    }

    @Test
    void readAndWriteMmsOverTpktCotp() throws Exception {
        peer = new FakeMmsPeer();
        peer.put("LD0/MMXU1.TotW.mag.f", 12.5f);
        peer.put("IED1/LLN0.Mod.stVal", 1.0f);
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new Iec61850DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "totw", "LD0/MMXU1.TotW.mag.f",
                "mod", "IED1/LLN0.Mod.stVal"
        ));
        assertEquals(12.5, (Double) object.variables.get("totw").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("mod").firstRow().get("value"), 0.001);
        assertEquals("good", object.variables.get("totw").firstRow().get("quality"));

        driver.writePoint("mod", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 2.0)
        ));
        assertEquals(2.0f, peer.get("IED1/LLN0.Mod.stVal"), 0.001f);
        assertEquals(2.0, (Double) object.variables.get("mod").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Iec61850DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "LD0/MMXU1.TotW.mag.f")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeMmsPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec61850-mms");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeMmsPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String token, float value) {
            values.put(token, value);
        }

        float get(String token) {
            return values.getOrDefault(token, 0.0f);
        }

        void start() {
            executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
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
                byte[] cr = Iec61850Codec.readTpkt(in);
                if (!Arrays.equals(cr, Iec61850Codec.connectionRequest())) {
                    return;
                }
                out.write(Iec61850Codec.encodeConnectionConfirm(1, 1));
                out.flush();
                while (true) {
                    byte[] tpkt = Iec61850Codec.readTpkt(in);
                    byte[] user = Iec61850Codec.unwrapDataTransfer(tpkt);
                    if (user.length == 0) {
                        return;
                    }
                    int tag = user[0] & 0xFF;
                    if (tag == 0x1A) {
                        String token = Iec61850Codec.decodeVisibleString(user);
                        float value = values.getOrDefault(token, 0.0f);
                        out.write(Iec61850Codec.encodeDataTransfer(
                                Iec61850Codec.encodeFloatingPoint(value)));
                        out.flush();
                    } else if (tag == 0x30) {
                        Iec61850Codec.WritePayload write = Iec61850Codec.decodeWritePayload(user);
                        values.put(write.reference(), write.value());
                        out.write(Iec61850Codec.encodeDataTransfer(
                                Iec61850Codec.encodeVisibleString("OK")));
                        out.flush();
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
                    "test-iec61850",
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
