package com.ispf.driver.omronfins;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Omron FINS driver — basic TCP memory area read.
 */
public class OmronFinsDeviceDriver implements DeviceDriver {

    private static final DataSchema READ_SCHEMA = DataSchema.builder("finsRead")
            .field("value", FieldType.STRING)
            .field("memoryArea", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "omron-fins",
            "Omron FINS Driver",
            "0.1.0",
            "Reads Omron PLC memory areas over FINS/TCP",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9600",
                    "destNode", "0",
                    "srcNode", "0"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9600;
    private int destNode = 0;
    private int srcNode = 0;
    private final Map<String, OmronFinsPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "destNode" -> destNode = Integer.parseInt(value.trim());
            case "srcNode" -> srcNode = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Omron FINS ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OmronFinsPoint point = OmronFinsPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readMemory(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Omron FINS driver is read-only in v0.1");
    }

    private DataRecord readMemory(OmronFinsPoint point) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendFinsHandshake(out, in);
            byte[] command = buildMemoryReadCommand(point);
            out.write(wrapFinsTcp(command));
            out.flush();

            byte[] response = readFinsResponse(in);
            String value = decodeMemoryValues(response, point.count());
            return DataRecord.single(READ_SCHEMA, Map.of(
                    "value", value,
                    "memoryArea", point.memoryArea(),
                    "address", point.address(),
                    "count", point.count()
            ));
        } catch (IOException e) {
            throw new DriverException("Omron FINS read failed for " + point, e);
        }
    }

    private void sendFinsHandshake(OutputStream out, InputStream in) throws IOException {
        byte[] handshake = new byte[] {
                'F', 'I', 'N', 'S',
                0, 0, 0, 12,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
        };
        out.write(handshake);
        out.flush();
        in.readNBytes(24);
    }

    private byte[] buildMemoryReadCommand(OmronFinsPoint point) {
        ByteBuffer buf = ByteBuffer.allocate(18);
        buf.put((byte) 0x80);
        buf.put((byte) 0x00);
        buf.put((byte) 0x02);
        buf.put((byte) 0x00);
        buf.put((byte) destNode);
        buf.put((byte) 0x00);
        buf.put((byte) 0x00);
        buf.put((byte) srcNode);
        buf.put((byte) 0x00);
        buf.put((byte) 0x01);
        buf.put((byte) 0x01);
        buf.put((byte) 0x01);
        buf.put(point.memoryAreaCode());
        buf.putShort((short) point.address());
        buf.put((byte) 0x00);
        buf.putShort((short) point.count());
        return buf.array();
    }

    private static byte[] wrapFinsTcp(byte[] finsFrame) {
        ByteBuffer buf = ByteBuffer.allocate(8 + finsFrame.length);
        buf.put(new byte[] { 'F', 'I', 'N', 'S' });
        buf.putInt(finsFrame.length);
        buf.put(finsFrame);
        return buf.array();
    }

    private static byte[] readFinsResponse(InputStream in) throws IOException {
        byte[] header = in.readNBytes(8);
        if (header.length < 8) {
            throw new IOException("Incomplete FINS TCP header");
        }
        int length = ByteBuffer.wrap(header, 4, 4).getInt();
        return in.readNBytes(length);
    }

    private static String decodeMemoryValues(byte[] response, int count) {
        if (response.length < 14) {
            return "";
        }
        int dataStart = 14;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count && dataStart + (i + 1) * 2 <= response.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            int hi = response[dataStart + i * 2] & 0xFF;
            int lo = response[dataStart + i * 2 + 1] & 0xFF;
            sb.append((hi << 8) | lo);
        }
        return sb.toString();
    }
}
