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
    private static final int LINK_CONTROL_UNCONFIRMED = 0x44;
    private static final int TRANSPORT_FIN_FIR = 0xC0;
    private static final int APP_REQUEST = 0xC0;
    private static final int APP_RESPONSE = 0xC0;
    private static final int FUNCTION_READ = 0x01;
    private static final int FUNCTION_RESPONSE = 0x81;
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
        return frame(masterAddress, outstationAddress, app.array(), sequence);
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
        return frame(outstationAddress, masterAddress, slice(app), sequence);
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

    public static int requestSequence(Frame frame) throws DriverException {
        byte[] application = frame.application();
        if (application.length < 2 || Byte.toUnsignedInt(application[1]) != FUNCTION_READ) {
            throw new DriverUnsupportedOperationException("Unsupported DNP3 request");
        }
        return application[0] & 0x0F;
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

    private static byte[] frame(int source, int destination, byte[] application, int sequence) {
        byte[] userData = new byte[1 + application.length];
        userData[0] = (byte) (TRANSPORT_FIN_FIR | (sequence & 0x3F));
        System.arraycopy(application, 0, userData, 1, application.length);
        return linkFrame(LINK_CONTROL_UNCONFIRMED, destination, source, userData);
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
