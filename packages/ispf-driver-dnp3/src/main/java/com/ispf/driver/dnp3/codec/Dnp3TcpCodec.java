package com.ispf.driver.dnp3.codec;

import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;
import com.ispf.driver.DriverUnsupportedOperationException;
import com.ispf.driver.dnp3.Dnp3Point;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Clean-room DNP3 TCP subset: link frame with CRC-16/DNP block structure,
 * single transport segment, and application integrity poll response objects for static points.
 */
public final class Dnp3TcpCodec {

    private static final int START_0 = 0x05;
    private static final int START_1 = 0x64;
    private static final int MAX_LENGTH = 255;
    private static final int USER_BLOCK = 16;
    private static final int LINK_CONTROL_RESET = 0xC0;
    private static final int LINK_CONTROL_MASTER_UNCONFIRMED = 0xC4;
    private static final int LINK_CONTROL_OUTSTATION_UNCONFIRMED = 0x44;
    private static final int TRANSPORT_FIN_FIR = 0xC0;
    private static final int APP_REQUEST = 0xC0;
    private static final int APP_RESPONSE = 0xC0;
    public static final int FUNCTION_READ = 0x01;
    public static final int FUNCTION_DIRECT_OPERATE = 0x05;
    private static final int FUNCTION_RESPONSE = 0x81;
    private static final int GROUP_CROB = 12;
    private static final int CROB_VARIATION = 1;
    private static final int GROUP_ANALOG_OUTPUT_CMD = 41;
    private static final int ANALOG_OUTPUT_FLOAT = 3;
    private static final int CROB_LATCH_ON = 0x03;
    private static final int CROB_LATCH_OFF = 0x04;
    private static final int OPERATE_SUCCESS = 0;
    private static final int QUALIFIER_16BIT_INDEXES = 0x28;
    /** CRC-16/DNP reflected polynomial (poly 0x3D65, refin/refout). */
    private static final int CRC_POLY_REFLECTED = 0xA6BC;

    private Dnp3TcpCodec() {
    }

    /**
     * Reset-link primary frame (control {@code 0xC0}): start, length, control, destination, source, CRC.
     */
    public static byte[] resetLinkRequest(int destination, int source) {
        return linkFrame(LINK_CONTROL_RESET, destination, source, new byte[0]);
    }

    public static byte[] integrityPollRequest(int masterAddress, int outstationAddress, int sequence) {
        ByteBuffer app = ByteBuffer.allocate(3 + 8).order(ByteOrder.LITTLE_ENDIAN);
        app.put((byte) (APP_REQUEST | (sequence & 0x0F)));
        app.put((byte) FUNCTION_READ);
        app.put((byte) 0x3C);
        app.put((byte) 0x01);
        app.put((byte) 0x06);
        app.put((byte) 0x3C);
        app.put((byte) 0x02);
        app.put((byte) 0x06);
        app.put((byte) 0x3C);
        app.put((byte) 0x03);
        app.put((byte) 0x06);
        return frame(masterAddress, outstationAddress, app.array(), sequence, true);
    }

    /**
     * Direct Operate (function {@code 0x05}) for one object.
     * Binary output is CROB g12v1 latch-on ({@code 0x03}) or latch-off ({@code 0x04}).
     * Analog output is g41v3 IEEE float32 plus a status byte. Qualifier is {@code 0x28}.
     */
    public static byte[] directOperateRequest(int masterAddress, int outstationAddress, int sequence,
            ControlCommand command) {
        byte[] object = encodeOperateObject(command, OPERATE_SUCCESS);
        ByteBuffer app = ByteBuffer.allocate(2 + object.length).order(ByteOrder.LITTLE_ENDIAN);
        app.put((byte) (APP_REQUEST | (sequence & 0x0F)));
        app.put((byte) FUNCTION_DIRECT_OPERATE);
        app.put(object);
        return frame(masterAddress, outstationAddress, slice(app), sequence, true);
    }

    /** Echo of a direct-operate request with status 0 and internal indications cleared. */
    public static byte[] directOperateResponse(int outstationAddress, int masterAddress, ControlCommand command,
            int sequence) {
        byte[] object = encodeOperateObject(command, OPERATE_SUCCESS);
        ByteBuffer app = ByteBuffer.allocate(4 + object.length).order(ByteOrder.LITTLE_ENDIAN);
        app.put((byte) (APP_RESPONSE | (sequence & 0x0F)));
        app.put((byte) FUNCTION_RESPONSE);
        app.putShort((short) 0);
        app.put(object);
        return frame(outstationAddress, masterAddress, slice(app), sequence, false);
    }

    public static byte[] integrityPollResponse(int outstationAddress, int masterAddress, List<Measurement> measurements,
            int sequence) {
        int payloadSize = 4 + measurements.stream().mapToInt(Dnp3TcpCodec::encodedSize).sum();
        ByteBuffer app = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        app.put((byte) (APP_RESPONSE | (sequence & 0x0F)));
        app.put((byte) FUNCTION_RESPONSE);
        app.putShort((short) 0);
        writeGroup(app, measurements, Dnp3Point.Dnp3DataType.BINARY_INPUT, 1, 2);
        writeGroup(app, measurements, Dnp3Point.Dnp3DataType.BINARY_OUTPUT, 10, 2);
        writeGroup(app, measurements, Dnp3Point.Dnp3DataType.COUNTER, 20, 1);
        writeGroup(app, measurements, Dnp3Point.Dnp3DataType.ANALOG_INPUT, 30, 5);
        writeGroup(app, measurements, Dnp3Point.Dnp3DataType.ANALOG_OUTPUT, 40, 1);
        return frame(outstationAddress, masterAddress, slice(app), sequence, false);
    }

    public static Frame readFrame(InputStream in) throws IOException, DriverException {
        int first = in.read();
        if (first < 0) {
            throw new EOFException("DNP3 stream closed");
        }
        int second = in.read();
        if (first != START_0 || second != START_1) {
            throw new DriverPermanentException("Invalid DNP3 start bytes");
        }
        int length = in.read();
        if (length < 5 || length > MAX_LENGTH) {
            throw new DriverPermanentException("Invalid DNP3 frame length " + length);
        }
        byte[] headerTail = readFully(in, 5);
        byte[] header = new byte[8];
        header[0] = (byte) START_0;
        header[1] = (byte) START_1;
        header[2] = (byte) length;
        System.arraycopy(headerTail, 0, header, 3, 5);
        int headerCrc = readCrcLe(in);
        if (headerCrc != crc16(header)) {
            throw new DriverPermanentException("DNP3 link header CRC mismatch");
        }
        int control = Byte.toUnsignedInt(header[3]);
        int destination = Byte.toUnsignedInt(header[4]) | (Byte.toUnsignedInt(header[5]) << 8);
        int source = Byte.toUnsignedInt(header[6]) | (Byte.toUnsignedInt(header[7]) << 8);
        int userLen = length - 5;
        byte[] userData = readUserData(in, userLen);
        if (userLen == 0) {
            return new Frame(control, source, destination, 0, new byte[0]);
        }
        int transport = Byte.toUnsignedInt(userData[0]);
        byte[] app = Arrays.copyOfRange(userData, 1, userLen);
        return new Frame(control, source, destination, transport, app);
    }

    public static void writeFrame(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    public static int applicationFunction(Frame frame) throws DriverException {
        byte[] application = frame.application();
        if (application.length < 2) {
            throw new DriverPermanentException("DNP3 application header too short");
        }
        return Byte.toUnsignedInt(application[1]);
    }

    public static int applicationSequence(Frame frame) throws DriverException {
        byte[] application = frame.application();
        if (application.length < 2) {
            throw new DriverPermanentException("DNP3 application header too short");
        }
        return application[0] & 0x0F;
    }

    public static int requestSequence(Frame frame) throws DriverException {
        if (applicationFunction(frame) != FUNCTION_READ) {
            throw new DriverUnsupportedOperationException("Unsupported DNP3 request");
        }
        return applicationSequence(frame);
    }

    public static ControlCommand parseDirectOperate(Frame frame) throws DriverException {
        return parseOperate(frame, false);
    }

    /** Accepts a direct-operate response whose echoed object status is 0. */
    public static void applyOperateResponse(Frame frame) throws DriverException {
        parseOperate(frame, true);
    }

    public static void applyResponse(Frame frame, MeasurementSink sink) throws DriverException {
        ByteBuffer app = ByteBuffer.wrap(frame.application()).order(ByteOrder.LITTLE_ENDIAN);
        int control = Byte.toUnsignedInt(app.get());
        int function = Byte.toUnsignedInt(app.get());
        if ((control & 0xC0) != APP_RESPONSE || function != FUNCTION_RESPONSE) {
            throw new DriverPermanentException("Unexpected DNP3 response");
        }
        app.getShort(); // internal indications
        while (app.hasRemaining()) {
            int group = Byte.toUnsignedInt(app.get());
            int variation = Byte.toUnsignedInt(app.get());
            int qualifier = Byte.toUnsignedInt(app.get());
            if (qualifier != QUALIFIER_16BIT_INDEXES) {
                throw new DriverUnsupportedOperationException(
                        "Unsupported DNP3 qualifier 0x" + Integer.toHexString(qualifier).toUpperCase(Locale.ROOT));
            }
            int count = Short.toUnsignedInt(app.getShort());
            for (int i = 0; i < count; i++) {
                int index = Short.toUnsignedInt(app.getShort());
                int flags = Byte.toUnsignedInt(app.get());
                switch (group) {
                    case 1, 10 -> sink.binary(group == 1 ? Dnp3Point.Dnp3DataType.BINARY_INPUT : Dnp3Point.Dnp3DataType.BINARY_OUTPUT,
                            index, app.get() != 0, flags);
                    case 20 -> sink.counter(index, Integer.toUnsignedLong(app.getInt()), flags);
                    case 30, 40 -> sink.analog(group == 30 ? Dnp3Point.Dnp3DataType.ANALOG_INPUT : Dnp3Point.Dnp3DataType.ANALOG_OUTPUT,
                            index, app.getDouble(), flags);
                    default -> skipUnsupported(variation);
                }
            }
        }
    }

    /**
     * CRC-16/DNP (init 0, poly 0x3D65 reflected as 0xA6BC, xorout 0xFFFF). Wire octets are little-endian.
     */
    static int crc16(byte[] data) {
        int crc = 0;
        for (byte datum : data) {
            crc ^= Byte.toUnsignedInt(datum);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ CRC_POLY_REFLECTED;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }

    private static void writeGroup(ByteBuffer app, List<Measurement> measurements, Dnp3Point.Dnp3DataType type,
            int group, int variation) {
        List<Measurement> filtered = new ArrayList<>();
        for (Measurement measurement : measurements) {
            if (measurement.type() == type) {
                filtered.add(measurement);
            }
        }
        if (filtered.isEmpty()) {
            return;
        }
        app.put((byte) group);
        app.put((byte) variation);
        app.put((byte) QUALIFIER_16BIT_INDEXES);
        app.putShort((short) filtered.size());
        for (Measurement measurement : filtered) {
            app.putShort((short) measurement.index());
            app.put((byte) measurement.flags());
            switch (type) {
                case BINARY_INPUT, BINARY_OUTPUT -> app.put((byte) ((Boolean) measurement.value() ? 1 : 0));
                case COUNTER -> app.putInt((int) ((Number) measurement.value()).longValue());
                case ANALOG_INPUT, ANALOG_OUTPUT -> app.putDouble(((Number) measurement.value()).doubleValue());
            }
        }
    }

    private static int encodedSize(Measurement measurement) {
        return switch (measurement.type()) {
            case BINARY_INPUT, BINARY_OUTPUT -> 5 + 2 + 1 + 1;
            case COUNTER -> 5 + 2 + 1 + 4;
            case ANALOG_INPUT, ANALOG_OUTPUT -> 5 + 2 + 1 + 8;
        };
    }

    private static byte[] frame(int source, int destination, byte[] application, int sequence, boolean fromMaster) {
        byte[] userData = new byte[1 + application.length];
        userData[0] = (byte) (TRANSPORT_FIN_FIR | (sequence & 0x3F));
        System.arraycopy(application, 0, userData, 1, application.length);
        int control = fromMaster ? LINK_CONTROL_MASTER_UNCONFIRMED : LINK_CONTROL_OUTSTATION_UNCONFIRMED;
        return linkFrame(control, destination, source, userData);
    }

    private static byte[] linkFrame(int control, int destination, int source, byte[] userData) {
        int length = 5 + userData.length;
        int blockCount = userData.length == 0 ? 0 : (userData.length + USER_BLOCK - 1) / USER_BLOCK;
        ByteBuffer frame = ByteBuffer.allocate(8 + 2 + userData.length + 2 * blockCount).order(ByteOrder.LITTLE_ENDIAN);
        byte[] header = new byte[8];
        header[0] = (byte) START_0;
        header[1] = (byte) START_1;
        header[2] = (byte) length;
        header[3] = (byte) control;
        header[4] = (byte) (destination & 0xFF);
        header[5] = (byte) ((destination >>> 8) & 0xFF);
        header[6] = (byte) (source & 0xFF);
        header[7] = (byte) ((source >>> 8) & 0xFF);
        frame.put(header);
        putCrcLe(frame, crc16(header));
        int offset = 0;
        while (offset < userData.length) {
            int n = Math.min(USER_BLOCK, userData.length - offset);
            frame.put(userData, offset, n);
            putCrcLe(frame, crc16(Arrays.copyOfRange(userData, offset, offset + n)));
            offset += n;
        }
        return frame.array();
    }

    private static byte[] readUserData(InputStream in, int userLen) throws IOException, DriverException {
        byte[] userData = new byte[userLen];
        int filled = 0;
        while (filled < userLen) {
            int n = Math.min(USER_BLOCK, userLen - filled);
            byte[] block = readFully(in, n);
            int blockCrc = readCrcLe(in);
            if (blockCrc != crc16(block)) {
                throw new DriverPermanentException("DNP3 user-data CRC mismatch");
            }
            System.arraycopy(block, 0, userData, filled, n);
            filled += n;
        }
        return userData;
    }

    private static void putCrcLe(ByteBuffer frame, int crc) {
        frame.put((byte) (crc & 0xFF));
        frame.put((byte) ((crc >>> 8) & 0xFF));
    }

    private static int readCrcLe(InputStream in) throws IOException {
        int low = in.read();
        int high = in.read();
        if (low < 0 || high < 0) {
            throw new EOFException("DNP3 stream closed in CRC");
        }
        return low | (high << 8);
    }

    private static void skipUnsupported(int variation) throws DriverException {
        throw new DriverUnsupportedOperationException("Unsupported DNP3 response object variation " + variation);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = in.read(data, offset, length - offset);
            if (count < 0) {
                throw new EOFException("DNP3 stream closed");
            }
            offset += count;
        }
        return data;
    }

    private static byte[] slice(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] encodeOperateObject(ControlCommand command, int status) {
        return switch (command.type()) {
            case BINARY_OUTPUT -> {
                boolean on = command.value() instanceof Boolean bool
                        ? bool
                        : ((Number) command.value()).doubleValue() != 0.0;
                ByteBuffer buffer = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
                buffer.put((byte) GROUP_CROB);
                buffer.put((byte) CROB_VARIATION);
                buffer.put((byte) QUALIFIER_16BIT_INDEXES);
                buffer.putShort((short) 1);
                buffer.putShort((short) command.index());
                buffer.put((byte) (on ? CROB_LATCH_ON : CROB_LATCH_OFF));
                buffer.put((byte) 1);
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.put((byte) status);
                yield slice(buffer);
            }
            case ANALOG_OUTPUT -> {
                ByteBuffer buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);
                buffer.put((byte) GROUP_ANALOG_OUTPUT_CMD);
                buffer.put((byte) ANALOG_OUTPUT_FLOAT);
                buffer.put((byte) QUALIFIER_16BIT_INDEXES);
                buffer.putShort((short) 1);
                buffer.putShort((short) command.index());
                buffer.putFloat(((Number) command.value()).floatValue());
                buffer.put((byte) status);
                yield slice(buffer);
            }
            default -> throw new IllegalArgumentException("DNP3 point type cannot be written: " + command.type());
        };
    }

    private static ControlCommand parseOperate(Frame frame, boolean response) throws DriverException {
        ByteBuffer app = ByteBuffer.wrap(frame.application()).order(ByteOrder.LITTLE_ENDIAN);
        if (app.remaining() < 2) {
            throw new DriverPermanentException("DNP3 application header too short");
        }
        int control = Byte.toUnsignedInt(app.get());
        int function = Byte.toUnsignedInt(app.get());
        int expectedControl = response ? APP_RESPONSE : APP_REQUEST;
        int expectedFunction = response ? FUNCTION_RESPONSE : FUNCTION_DIRECT_OPERATE;
        if ((control & 0xC0) != expectedControl || function != expectedFunction) {
            throw new DriverPermanentException("Unexpected DNP3 operate header");
        }
        if (response) {
            if (app.remaining() < 2) {
                throw new DriverPermanentException("DNP3 operate response missing indications");
            }
            app.get();
            int iin2 = Byte.toUnsignedInt(app.get());
            if ((iin2 & 0x17) != 0) {
                throw new DriverPermanentException("DNP3 operate rejected by IIN2=0x"
                        + Integer.toHexString(iin2).toUpperCase(Locale.ROOT));
            }
        }
        if (app.remaining() < 7) {
            throw new DriverPermanentException("DNP3 operate object header truncated");
        }
        int group = Byte.toUnsignedInt(app.get());
        int variation = Byte.toUnsignedInt(app.get());
        int qualifier = Byte.toUnsignedInt(app.get());
        if (qualifier != QUALIFIER_16BIT_INDEXES) {
            throw new DriverUnsupportedOperationException(
                    "DNP3 operate qualifier 0x" + Integer.toHexString(qualifier).toUpperCase(Locale.ROOT));
        }
        int count = Short.toUnsignedInt(app.getShort());
        if (count != 1) {
            throw new DriverPermanentException("DNP3 operate accepts one object, got " + count);
        }
        int index = Short.toUnsignedInt(app.getShort());
        if (group == GROUP_CROB && variation == CROB_VARIATION) {
            if (app.remaining() < 11) {
                throw new DriverPermanentException("DNP3 CROB truncated");
            }
            int code = Byte.toUnsignedInt(app.get());
            app.get();
            app.getInt();
            app.getInt();
            int status = Byte.toUnsignedInt(app.get());
            if (response && status != OPERATE_SUCCESS) {
                throw new DriverPermanentException("DNP3 operate rejected, status " + status);
            }
            boolean on = switch (code) {
                case CROB_LATCH_ON -> true;
                case CROB_LATCH_OFF -> false;
                default -> throw new DriverPermanentException("DNP3 CROB code 0x"
                        + Integer.toHexString(code).toUpperCase(Locale.ROOT));
            };
            return new ControlCommand(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, index, on);
        }
        if (group == GROUP_ANALOG_OUTPUT_CMD && variation == ANALOG_OUTPUT_FLOAT) {
            if (app.remaining() < 5) {
                throw new DriverPermanentException("DNP3 analog output truncated");
            }
            double value = app.getFloat();
            int status = Byte.toUnsignedInt(app.get());
            if (response && status != OPERATE_SUCCESS) {
                throw new DriverPermanentException("DNP3 operate rejected, status " + status);
            }
            return new ControlCommand(Dnp3Point.Dnp3DataType.ANALOG_OUTPUT, index, value);
        }
        throw new DriverPermanentException("DNP3 operate group " + group + " variation " + variation);
    }

    public record ControlCommand(Dnp3Point.Dnp3DataType type, int index, Object value) {
    }

    public static final class Frame {
        private final int control;
        private final int source;
        private final int destination;
        private final int transport;
        private final byte[] application;

        Frame(int control, int source, int destination, int transport, byte[] application) {
            this.control = control;
            this.source = source;
            this.destination = destination;
            this.transport = transport;
            this.application = application == null ? new byte[0] : application.clone();
        }

        public int control() {
            return control;
        }

        public int source() {
            return source;
        }

        public int destination() {
            return destination;
        }

        public int transport() {
            return transport;
        }

        public byte[] application() {
            return application.clone();
        }
    }

    public record Measurement(Dnp3Point.Dnp3DataType type, int index, Object value, int flags) {
    }

    public interface MeasurementSink {
        void binary(Dnp3Point.Dnp3DataType type, int index, boolean value, int flags);

        void analog(Dnp3Point.Dnp3DataType type, int index, double value, int flags);

        void counter(int index, long value, int flags);
    }
}
