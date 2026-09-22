package com.ispf.driver.enocean;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.enocean.codec.Esp3Codec;
import com.ispf.driver.enocean.codec.Esp3Packet;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnoceanDeviceDriverTest {

    private EnoceanDeviceDriver driver;
    private FakeEsp3Peer peer;

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
    void coRdVersionRequestIsHandwritten55LiteralWithDefensibleCrc() {
        // Handwritten published ESP3 CO_RD_VERSION request (EnOcean COMMON_COMMAND 0x03).
        byte[] expected = new byte[] {
                (byte) 0x55,
                0x00, 0x01,
                0x00,
                0x05,
                0x70,
                0x03,
                0x09
        };
        assertEquals(0x55, expected[0] & 0xFF);
        assertEquals(0x70, expected[5] & 0xFF); // header CRC written by hand
        assertEquals(0x09, expected[7] & 0xFF); // data CRC written by hand

        // Independent MSB-first poly 0x07 (not a production table copy-paste).
        assertEquals(0x70, independentCrc8(new byte[] { 0x00, 0x01, 0x00, 0x05 }));
        assertEquals(0x09, independentCrc8(new byte[] { 0x03 }));

        assertArrayEquals(expected, EnoceanDeviceDriver.coRdVersionRequestLiteral());
        assertArrayEquals(expected, Esp3Codec.coRdVersionRequestLiteral());
        assertArrayEquals(expected, Esp3Codec.encode(
                Esp3Codec.TYPE_COMMON_COMMAND, new byte[] { Esp3Codec.CO_RD_VERSION }, new byte[0]));
    }

    @Test
    void metadataIsProductionEsp3() {
        driver = new EnoceanDeviceDriver();
        assertEquals("enocean", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("esp3") || description.contains("0x55"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void radioReadWriteLoopback() throws Exception {
        peer = new FakeEsp3Peer();
        peer.put(new byte[] { (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD },
                new byte[] { 0x01, (byte) 0xFF });
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new EnoceanDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("sw", "AABBCCDD"));
        assertEquals("01FF", object.variables.get("sw").firstRow().get("value"));

        driver.writePoint("sw", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "00AA")
        ));
        assertArrayEquals(new byte[] { 0x00, (byte) 0xAA }, peer.get(
                new byte[] { (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD }));
    }

    /**
     * Independent ESP3 CRC8 (poly 0x07, init 0, MSB-first) — bit algorithm, not the production path.
     */
    static int independentCrc8(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0x07) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
        }
        return crc;
    }

    private static final class FakeEsp3Peer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-esp3");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, byte[]> payloads = new ConcurrentHashMap<>();

        FakeEsp3Peer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(byte[] deviceId, byte[] data) {
            payloads.put(EnoceanDeviceDriver.toHex(deviceId), data.clone());
        }

        byte[] get(byte[] deviceId) {
            return payloads.get(EnoceanDeviceDriver.toHex(deviceId));
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
                    Esp3Packet packet = Esp3Codec.readPacket(in);
                    if (packet.packetType() != Esp3Codec.TYPE_RADIO || packet.data().length < 4) {
                        Esp3Codec.writePacket(out, Esp3Codec.encodeResponseOk());
                        continue;
                    }
                    byte[] deviceId = packet.deviceId();
                    byte[] payload = packet.payload();
                    String key = EnoceanDeviceDriver.toHex(deviceId);
                    if (payload.length == 0) {
                        byte[] stored = payloads.getOrDefault(key, new byte[0]);
                        Esp3Codec.writePacket(out, Esp3Codec.encodeRadio(deviceId, stored));
                    } else {
                        payloads.put(key, payload);
                        Esp3Codec.writePacket(out, Esp3Codec.encodeResponseOk());
                    }
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
            return new PlatformObject(
                    "test-enocean", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
