package com.ispf.driver.wmbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.wmbus.codec.WmbusCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
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

class WmbusDeviceDriverTest {

    private WmbusDeviceDriver driver;
    private FakeWmbusPeer peer;

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
    void metadataDescribesTcpFormatANotRfPhy() {
        driver = new WmbusDeviceDriver();
        assertEquals("wmbus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("en 13757") || description.contains("format-a") || description.contains("crc"));
        assertTrue(description.contains("not rf") || description.contains("not an rf"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsMeterAndIdForms() throws Exception {
        assertEquals(new WmbusPoint(WmbusPoint.Kind.METER_INDEX, "1"), WmbusPoint.parse("meter:1"));
        assertEquals(new WmbusPoint(WmbusPoint.Kind.DEVICE_ID, "AABBCCDD"), WmbusPoint.parse("id:AABBCCDD"));
        assertEquals(new WmbusPoint(WmbusPoint.Kind.METER_INDEX, "2"), WmbusPoint.parse("2"));
    }

    @Test
    void crc16MatchesCatalogCheckAndIndependentMsb() {
        byte[] check = "123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(0xC2B7, WmbusCodec.crc16(check));
        assertEquals(0xC2B7, independentCrc16(check));
        byte[] sample = new byte[] { 0x01, 0x02 };
        assertEquals(independentCrc16(sample), WmbusCodec.crc16(sample));
    }

    @Test
    void pollMeterAndIdReturnsParsedCiValue() throws Exception {
        peer = new FakeWmbusPeer();
        peer.putMeter(1, WmbusCodec.encodeTelegram(0x1B2C, 0xAABBCCDDL, 1, 7, 12.5f));
        peer.putId("AABBCCDD", WmbusCodec.encodeTelegram(0x1B2C, 0xAABBCCDDL, 1, 7, 12.5f));
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
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
        assertEquals(0x7AL, object.variables.get("m1").firstRow().get("ci"));
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

    /**
     * Independent CRC-16/EN-13757, MSB first, xorout 0xFFFF.
     * Must not call {@link WmbusCodec#crc16(byte[])}.
     */
    private static int independentCrc16(byte[] data) {
        int crc = 0x0000;
        for (byte value : data) {
            crc ^= (value & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x3D65) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }

    private static final class FakeWmbusPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-wmbus");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, byte[]> byToken = new ConcurrentHashMap<>();

        FakeWmbusPeer() throws IOException {
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
                    byte[] request = WmbusCodec.readFrame(in);
                    WmbusCodec.ParsedTelegram parsed = WmbusCodec.parse(request);
                    String token;
                    if (parsed.manufacturer() == 0) {
                        token = "meter:" + parsed.deviceId();
                    } else {
                        token = "id:" + WmbusCodec.deviceIdHex(parsed.deviceId());
                    }
                    byte[] response = byToken.get(token);
                    if (response == null) {
                        throw new EOFException("unknown token " + token);
                    }
                    out.write(response);
                    out.flush();
                }
            } catch (IOException ignored) {
                // closed
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
