package com.ispf.driver.ethernetip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EthernetIpDeviceDriverTest {

    private static final int ENCAP_REGISTER_SESSION = 0x0065;
    private static final int ENCAP_SEND_RR_DATA = 0x006F;
    private static final int CIP_READ_TAG = 0x4C;
    private static final int CIP_WRITE_TAG = 0x4D;
    private static final int CIP_DINT = 0xC4;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int port;

    private final AtomicInteger lastWriteType = new AtomicInteger(-1);
    private final AtomicInteger lastWriteValue = new AtomicInteger(Integer.MIN_VALUE);

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            executor = null;
        }
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
    }

    @Test
    void readsAtomicTagViaLoopback() throws Exception {
        startCipEmulator();
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(loopbackConfig());
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("counter", "Program:MainProgram.Counter"));

        assertTrue(driver.isConnected());
        DataRecord record = driverObject.variables.get("counter");
        assertEquals("12345678", record.firstRow().get("value"));
        assertEquals("GOOD", record.firstRow().get("quality"));
        assertEquals("Program:MainProgram.Counter", record.firstRow().get("tagPath"));
        assertEquals(42L, ((Number) record.firstRow().get("sessionHandle")).longValue());
        driver.disconnect();
    }

    @Test
    void unknownTagReadsBadQuality() throws Exception {
        startCipEmulator();
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(loopbackConfig());
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("ghost", "NoSuchTag"));

        DataRecord record = driverObject.variables.get("ghost");
        assertEquals("", record.firstRow().get("value"));
        assertTrue(String.valueOf(record.firstRow().get("quality")).contains("0x4"));
        driver.disconnect();
    }

    @Test
    void writesAtomicTagViaLoopback() throws Exception {
        startCipEmulator();
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(loopbackConfig());
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("counter", "Program:MainProgram.Counter"));

        driver.writePoint("counter", DataRecord.single(
                com.ispf.core.model.DataSchema.builder("write")
                        .field("value", com.ispf.core.model.FieldType.INTEGER)
                        .build(),
                Map.of("value", 555)
        ));

        assertEquals(CIP_DINT, lastWriteType.get());
        assertEquals(555, lastWriteValue.get());
        assertEquals("555", driverObject.variables.get("counter").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeToUnmappedPointFails() throws Exception {
        startCipEmulator();
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(loopbackConfig());
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("never-polled", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("write")
                                .field("value", com.ispf.core.model.FieldType.INTEGER)
                                .build(),
                        Map.of("value", 1)
                )));
        assertTrue(error.getMessage().contains("not mapped"));
        driver.disconnect();
    }

    @Test
    void connectionRefusedFailsConnect() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(freePort),
                "timeoutMs", "3000"
        )));

        assertThrows(DriverException.class, driver::connect);
    }

    private Map<String, String> loopbackConfig() {
        return Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "5000"
        );
    }

    /** Minimal EtherNet/IP emulator: RegisterSession + UCMM Read/Write Tag for one DINT tag. */
    private void startCipEmulator() throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        port = serverSocket.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                client.setSoTimeout(10000);
                DataInputStream in = new DataInputStream(client.getInputStream());
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                while (true) {
                    byte[] header = in.readNBytes(24);
                    if (header.length < 24) {
                        return;
                    }
                    ByteBuffer request = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                    int command = Short.toUnsignedInt(request.getShort(0));
                    int length = Short.toUnsignedInt(request.getShort(2));
                    byte[] payload = length > 0 ? in.readNBytes(length) : new byte[0];
                    if (payload.length < length) {
                        return;
                    }
                    if (command == ENCAP_REGISTER_SESSION) {
                        ByteBuffer body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        body.putShort((short) 1).putShort((short) 0);
                        writeEncapFrame(out, ENCAP_REGISTER_SESSION, body.array());
                    } else if (command == ENCAP_SEND_RR_DATA) {
                        byte[] cipReply = handleCip(extractCipRequest(payload));
                        writeEncapFrame(out, ENCAP_SEND_RR_DATA, buildSendRrDataReply(cipReply));
                    }
                }
            } catch (Exception ignored) {
                // loopback test best effort
            }
        });
    }

    private byte[] handleCip(byte[] cip) {
        int service = cip[0] & 0xFF;
        int pathWords = cip[1] & 0xFF;
        byte[] pathBytes = new byte[pathWords * 2];
        System.arraycopy(cip, 2, pathBytes, 0, pathBytes.length);
        String tag = decodeSymbolicPath(pathBytes);
        if (service == CIP_READ_TAG) {
            if ("Program:MainProgram.Counter".equals(tag)) {
                ByteBuffer reply = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
                reply.put((byte) (CIP_READ_TAG | 0x80));
                reply.put((byte) 0);
                reply.put((byte) 0);          // general status OK
                reply.put((byte) 0);          // additional status size
                reply.putShort((short) CIP_DINT);
                reply.putInt(12345678);
                return reply.array();
            }
            return cipReply(CIP_READ_TAG, 0x04); // path segment error
        }
        if (service == CIP_WRITE_TAG) {
            ByteBuffer writeData = ByteBuffer.wrap(cip, 2 + pathBytes.length, cip.length - 2 - pathBytes.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            lastWriteType.set(Short.toUnsignedInt(writeData.getShort()));
            writeData.getShort();             // element count
            lastWriteValue.set(writeData.getInt());
            return cipReply(CIP_WRITE_TAG, 0);
        }
        return cipReply(service, 0x08);       // service not supported
    }

    private static byte[] cipReply(int service, int generalStatus) {
        return new byte[]{(byte) (service | 0x80), 0, (byte) generalStatus, 0};
    }

    private static byte[] extractCipRequest(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt();                      // interface handle
        buffer.getShort();                    // timeout
        int itemCount = Short.toUnsignedInt(buffer.getShort());
        for (int i = 0; i < itemCount; i++) {
            int typeId = Short.toUnsignedInt(buffer.getShort());
            int length = Short.toUnsignedInt(buffer.getShort());
            if (typeId == 0x00B2) {
                byte[] data = new byte[length];
                buffer.get(data);
                return data;
            }
            buffer.position(buffer.position() + length);
        }
        return new byte[0];
    }

    private static byte[] buildSendRrDataReply(byte[] cipReply) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + cipReply.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 2);
        buffer.putShort((short) 0x0000);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0x00B2);
        buffer.putShort((short) cipReply.length);
        buffer.put(cipReply);
        return buffer.array();
    }

    private static void writeEncapFrame(DataOutputStream out, int command, byte[] payload) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) command);
        header.putShort((short) payload.length);
        header.putInt(42);                    // session handle
        header.putInt(0);                     // status
        header.putLong(0L);                   // sender context
        header.putInt(0);                     // options
        out.write(header.array());
        out.write(payload);
        out.flush();
    }

    private static String decodeSymbolicPath(byte[] path) {
        StringBuilder tag = new StringBuilder();
        int i = 0;
        while (i + 1 < path.length) {
            if ((path[i] & 0xFF) != 0x91) {
                break;
            }
            int length = path[i + 1] & 0xFF;
            if (tag.length() > 0) {
                tag.append('.');
            }
            tag.append(new String(path, i + 2, length, StandardCharsets.US_ASCII));
            i += 2 + length + (length % 2 == 1 ? 1 : 0);
        }
        return tag.toString();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-ethernet-ip",
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
