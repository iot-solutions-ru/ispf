package com.ispf.driver.dnp3.codec;

import com.ispf.driver.DriverException;
import com.ispf.driver.dnp3.Dnp3Point;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room DNP3 TCP subset: link frame, single transport segment, and
 * application integrity poll response objects for static points.
 */
public final class Dnp3TcpCodec {

    private static final int START_0 = 0x05;
    private static final int START_1 = 0x64;
    private static final int MAX_FRAME = 4096;
    private static final int TRANSPORT_FIN_FIR = 0xC0;
    private static final int APP_REQUEST = 0xC0;
    private static final int APP_RESPONSE = 0xC0;
    private static final int FUNCTION_READ = 0x01;
    private static final int FUNCTION_RESPONSE = 0x81;
    private static final int QUALIFIER_16BIT_INDEXES = 0x28;

    private Dnp3TcpCodec() {
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
            throw new DriverException("Invalid DNP3 start bytes");
        }
        int length = in.read();
        if (length < 5 || length > MAX_FRAME) {
            throw new DriverException("Invalid DNP3 frame length " + length);
        }
        byte[] body = readFully(in, length);
        int sentCrcLow = in.read();
        int sentCrcHigh = in.read();
        if (sentCrcLow < 0 || sentCrcHigh < 0) {
            throw new EOFException("DNP3 stream closed in CRC");
        }
        int sentCrc = sentCrcLow | (sentCrcHigh << 8);
        int actualCrc = crc16(body);
        if (sentCrc != actualCrc) {
            throw new DriverException("DNP3 frame CRC mismatch");
        }
        ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int control = Byte.toUnsignedInt(buffer.get());
        int destination = Short.toUnsignedInt(buffer.getShort());
        int source = Short.toUnsignedInt(buffer.getShort());
        int transport = Byte.toUnsignedInt(buffer.get());
        byte[] app = new byte[buffer.remaining()];
        buffer.get(app);
        return new Frame(control, source, destination, transport, app);
    }

    public static void writeFrame(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    public static int requestSequence(Frame frame) throws DriverException {
        if (frame.application().length < 2 || Byte.toUnsignedInt(frame.application()[1]) != FUNCTION_READ) {
            throw new DriverException("Unsupported DNP3 request");
        }
        return frame.application()[0] & 0x0F;
    }

    public static void applyResponse(Frame frame, MeasurementSink sink) throws DriverException {
        ByteBuffer app = ByteBuffer.wrap(frame.application()).order(ByteOrder.LITTLE_ENDIAN);
        int control = Byte.toUnsignedInt(app.get());
        int function = Byte.toUnsignedInt(app.get());
        if ((control & 0xC0) != APP_RESPONSE || function != FUNCTION_RESPONSE) {
            throw new DriverException("Unexpected DNP3 response");
        }
        app.getShort(); // internal indications
        while (app.hasRemaining()) {
            int group = Byte.toUnsignedInt(app.get());
            int variation = Byte.toUnsignedInt(app.get());
            int qualifier = Byte.toUnsignedInt(app.get());
            if (qualifier != QUALIFIER_16BIT_INDEXES) {
                throw new DriverException("Unsupported DNP3 qualifier 0x" + Integer.toHexString(qualifier));
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
                    default -> skipUnsupported(app, variation);
                }
            }
        }
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
        ByteBuffer body = ByteBuffer.allocate(1 + 2 + 2 + 1 + application.length).order(ByteOrder.LITTLE_ENDIAN);
        body.put((byte) 0x44);
        body.putShort((short) destination);
        body.putShort((short) source);
        body.put((byte) (TRANSPORT_FIN_FIR | (sequence & 0x3F)));
        body.put(application);
        byte[] bodyBytes = body.array();
        ByteBuffer frame = ByteBuffer.allocate(3 + bodyBytes.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        frame.put((byte) START_0);
        frame.put((byte) START_1);
        frame.put((byte) bodyBytes.length);
        frame.put(bodyBytes);
        frame.putShort((short) crc16(bodyBytes));
        return frame.array();
    }

    private static void skipUnsupported(ByteBuffer app, int variation) throws DriverException {
        throw new DriverException("Unsupported DNP3 response object variation " + variation);
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

    private static int crc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte datum : data) {
            crc ^= Byte.toUnsignedInt(datum);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public record Frame(int control, int source, int destination, int transport, byte[] application) {
    }

    public record Measurement(Dnp3Point.Dnp3DataType type, int index, Object value, int flags) {
    }

    public interface MeasurementSink {
        void binary(Dnp3Point.Dnp3DataType type, int index, boolean value, int flags);

        void analog(Dnp3Point.Dnp3DataType type, int index, double value, int flags);

        void counter(int index, long value, int flags);
    }
}
