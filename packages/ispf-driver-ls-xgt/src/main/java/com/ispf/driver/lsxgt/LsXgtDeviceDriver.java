package com.ispf.driver.lsxgt;

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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LS Electric XGT dedicated protocol driver — binary framing over TCP (default port 2004).
 * <p>
 * This is an XGT dedicated read over TCP, not a certified FEnet stack. Frames use the company
 * header {@code LSIS-XGT} plus two zero bytes, then PLC/CPU info, source-of-frame, invoke id,
 * instruction length, slot, and BCC (sum of header bytes 0..18 modulo 256).
 * <p>
 * Read instruction (little-endian): command {@code 0x0054}, data type word {@code 0x0002}
 * (bit {@code 0x0000} for MX), reserved 0, block count, then per block variable length + ASCII
 * name ({@code %DW0}, {@code %MW10}, {@code %MX0}). Write uses command {@code 0x0058} with the
 * same variable naming and a trailing data-size + payload.
 * <p>
 * Point mapping: {@code %DW100}, {@code DW100}, {@code %MW10}, {@code %MX0} — see {@link LsXgtPoint}.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only; no PLC4X, no vendor SDK, no GPL.
 */
public class LsXgtDeviceDriver implements DeviceDriver {

    static final byte[] COMPANY_ID = "LSIS-XGT\0\0".getBytes(StandardCharsets.US_ASCII);
    static final int HEADER_LEN = 20;
    static final int CMD_READ = 0x0054;
    static final int CMD_WRITE = 0x0058;
    static final int DATA_TYPE_BIT = 0x0000;
    static final int DATA_TYPE_WORD = 0x0002;
    static final byte SOF_REQUEST = 0x33;
    static final byte SOF_RESPONSE = 0x11;

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("lsXgtValue")
            .field("value", FieldType.STRING)
            .field("device", FieldType.STRING)
            .field("address", FieldType.INTEGER)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ls-xgt",
            "LS XGT Driver",
            "0.1.0",
            "XGT dedicated read over TCP (not a certified FEnet stack)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2004",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2004;
    private int timeoutMs = 3000;
    private final AtomicInteger invokeId = new AtomicInteger();
    private final Map<String, LsXgtPoint> points = new ConcurrentHashMap<>();
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
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "LS XGT dedicated ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
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
            LsXgtPoint point = LsXgtPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readDevice(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        LsXgtPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        int word = (int) extractNumeric(value) & 0xFFFF;
        writeDevice(point, word);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", String.valueOf(word),
                "device", point.deviceType().name(),
                "address", point.address(),
                "count", 1
        )));
    }

    private DataRecord readDevice(LsXgtPoint point) throws DriverException {
        byte[] request = encodeRead(point, invokeId.incrementAndGet() & 0xFFFF);
        byte[] response = transact(request);
        short[] words = decodeReadResponse(response, point);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(words[i] & 0xFFFF);
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", sb.toString(),
                "device", point.deviceType().name(),
                "address", point.address(),
                "count", point.count()
        ));
    }

    private void writeDevice(LsXgtPoint point, int word) throws DriverException {
        LsXgtPoint single = new LsXgtPoint(point.deviceType(), point.address(), 1);
        byte[] request = encodeWrite(single, invokeId.incrementAndGet() & 0xFFFF, word);
        byte[] response = transact(request);
        decodeWriteResponse(response);
    }

    /**
     * Encodes an XGT dedicated individual read for the given point and invoke id.
     */
    static byte[] encodeRead(LsXgtPoint point, int invoke) {
        int dataType = point.deviceType() == LsXgtPoint.DeviceType.MX ? DATA_TYPE_BIT : DATA_TYPE_WORD;
        int blocks = point.count();
        int instrLen = 8; // command + type + reserved + block count
        byte[][] names = new byte[blocks][];
        for (int i = 0; i < blocks; i++) {
            names[i] = point.variableName(i).getBytes(StandardCharsets.US_ASCII);
            instrLen += 2 + names[i].length;
        }
        ByteBuffer instr = ByteBuffer.allocate(instrLen).order(ByteOrder.LITTLE_ENDIAN);
        instr.putShort((short) CMD_READ);
        instr.putShort((short) dataType);
        instr.putShort((short) 0);
        instr.putShort((short) blocks);
        for (byte[] name : names) {
            instr.putShort((short) name.length);
            instr.put(name);
        }
        return wrapFrame(instr.array(), invoke);
    }

    /**
     * Encodes an XGT dedicated individual write (one word/bit) for the given point.
     */
    static byte[] encodeWrite(LsXgtPoint point, int invoke, int word) {
        int dataType = point.deviceType() == LsXgtPoint.DeviceType.MX ? DATA_TYPE_BIT : DATA_TYPE_WORD;
        byte[] name = point.variableName(0).getBytes(StandardCharsets.US_ASCII);
        int dataBytes = dataType == DATA_TYPE_BIT ? 1 : 2;
        int instrLen = 8 + 2 + name.length + 2 + dataBytes;
        ByteBuffer instr = ByteBuffer.allocate(instrLen).order(ByteOrder.LITTLE_ENDIAN);
        instr.putShort((short) CMD_WRITE);
        instr.putShort((short) dataType);
        instr.putShort((short) 0);
        instr.putShort((short) 1);
        instr.putShort((short) name.length);
        instr.put(name);
        instr.putShort((short) dataBytes);
        if (dataType == DATA_TYPE_BIT) {
            instr.put((byte) (word & 0x01));
        } else {
            instr.putShort((short) (word & 0xFFFF));
        }
        return wrapFrame(instr.array(), invoke);
    }

    static byte[] wrapFrame(byte[] instruction, int invoke) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + instruction.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(COMPANY_ID);
        buf.putShort((short) 0); // PLC info
        buf.put((byte) 0); // CPU info
        buf.put(SOF_REQUEST);
        buf.putShort((short) (invoke & 0xFFFF));
        buf.putShort((short) instruction.length);
        buf.put((byte) 0); // slot
        int bcc = 0;
        byte[] soFar = buf.array();
        for (int i = 0; i < 19; i++) {
            bcc = (bcc + (soFar[i] & 0xFF)) & 0xFF;
        }
        buf.put((byte) bcc);
        buf.put(instruction);
        return buf.array();
    }

    static short[] decodeReadResponse(byte[] frame, LsXgtPoint point) throws DriverException {
        ByteBuffer body = instructionBody(frame, CMD_READ);
        int dataType = body.getShort() & 0xFFFF;
        int error = body.getShort() & 0xFFFF;
        if (error != 0) {
            throw new DriverException("XGT dedicated read error status " + error);
        }
        int blocks = body.getShort() & 0xFFFF;
        if (blocks != point.count()) {
            throw new DriverException("XGT dedicated read block count mismatch");
        }
        short[] words = new short[blocks];
        for (int i = 0; i < blocks; i++) {
            int dataSize = body.getShort() & 0xFFFF;
            if (dataType == DATA_TYPE_BIT) {
                if (dataSize < 1 || body.remaining() < dataSize) {
                    throw new DriverException("Truncated XGT dedicated bit payload");
                }
                words[i] = (short) (body.get() & 0x01);
                for (int skip = 1; skip < dataSize; skip++) {
                    body.get();
                }
            } else {
                if (dataSize < 2 || body.remaining() < dataSize) {
                    throw new DriverException("Truncated XGT dedicated word payload");
                }
                words[i] = body.getShort();
                for (int skip = 2; skip < dataSize; skip++) {
                    body.get();
                }
            }
        }
        return words;
    }

    static void decodeWriteResponse(byte[] frame) throws DriverException {
        ByteBuffer body = instructionBody(frame, CMD_WRITE);
        body.getShort(); // data type
        int error = body.getShort() & 0xFFFF;
        if (error != 0) {
            throw new DriverException("XGT dedicated write error status " + error);
        }
    }

    private static ByteBuffer instructionBody(byte[] frame, int expectedCommand) throws DriverException {
        if (frame.length < HEADER_LEN) {
            throw new DriverException("Truncated XGT dedicated header");
        }
        if (!Arrays.equals(Arrays.copyOf(frame, COMPANY_ID.length), COMPANY_ID)) {
            throw new DriverException("Invalid XGT dedicated company id");
        }
        int instrLen = (frame[16] & 0xFF) | ((frame[17] & 0xFF) << 8);
        if (frame.length < HEADER_LEN + instrLen) {
            throw new DriverException("Truncated XGT dedicated instruction");
        }
        ByteBuffer body = ByteBuffer.wrap(frame, HEADER_LEN, instrLen).order(ByteOrder.LITTLE_ENDIAN);
        int command = body.getShort() & 0xFFFF;
        if (command != expectedCommand) {
            throw new DriverException("Unexpected XGT dedicated command in response");
        }
        return body;
    }

    private byte[] transact(byte[] request) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();

            byte[] header = in.readNBytes(HEADER_LEN);
            if (header.length < HEADER_LEN) {
                throw new IOException("Incomplete XGT dedicated header");
            }
            int instrLen = (header[16] & 0xFF) | ((header[17] & 0xFF) << 8);
            byte[] instruction = instrLen > 0 ? in.readNBytes(instrLen) : new byte[0];
            if (instruction.length < instrLen) {
                throw new IOException("Truncated XGT dedicated instruction");
            }
            byte[] response = new byte[HEADER_LEN + instruction.length];
            System.arraycopy(header, 0, response, 0, HEADER_LEN);
            System.arraycopy(instruction, 0, response, HEADER_LEN, instruction.length);
            return response;
        } catch (IOException e) {
            throw new DriverException("LS XGT dedicated I/O failed for " + host + ":" + port, e);
        }
    }

    private static long extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("LS XGT write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.longValue();
            }
            if (candidate != null) {
                String text = String.valueOf(candidate);
                int comma = text.indexOf(',');
                return Long.parseLong(comma < 0 ? text.trim() : text.substring(0, comma).trim());
            }
        }
        throw new IllegalArgumentException("LS XGT write requires numeric raw/value field");
    }
}
