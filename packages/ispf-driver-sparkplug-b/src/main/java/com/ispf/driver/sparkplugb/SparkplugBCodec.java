package com.ispf.driver.sparkplugb;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal clean-room Sparkplug B protobuf codec for Payload/Metric (string/int/float/bool).
 * <p>
 * Wire layout matches Eclipse Tahu {@code sparkplug_b.proto} field numbers for the subset used here.
 * No third-party protobuf dependency.
 */
public final class SparkplugBCodec {

    public static final int DATATYPE_INT32 = 3;
    public static final int DATATYPE_FLOAT = 9;
    public static final int DATATYPE_BOOLEAN = 11;
    public static final int DATATYPE_STRING = 12;

    private SparkplugBCodec() {
    }

    public record Metric(String name, int dataType, Object value) {
        public Metric {
            Objects.requireNonNull(name, "name");
        }
    }

    public record Payload(Long timestamp, List<Metric> metrics, Long seq) {
        public Payload {
            metrics = List.copyOf(metrics == null ? List.of() : metrics);
        }
    }

    public static byte[] encode(Payload payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (payload.timestamp() != null) {
            writeTag(out, 1, 0);
            writeVarint(out, payload.timestamp());
        }
        for (Metric metric : payload.metrics()) {
            byte[] encodedMetric = encodeMetric(metric);
            writeTag(out, 2, 2);
            writeVarint(out, encodedMetric.length);
            out.writeBytes(encodedMetric);
        }
        if (payload.seq() != null) {
            writeTag(out, 3, 0);
            writeVarint(out, payload.seq());
        }
        return out.toByteArray();
    }

    public static Payload decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new Payload(null, List.of(), null);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        Long timestamp = null;
        Long seq = null;
        List<Metric> metrics = new ArrayList<>();
        while (buf.hasRemaining()) {
            long key = readVarint(buf);
            int field = (int) (key >>> 3);
            int wire = (int) (key & 0x7);
            switch (field) {
                case 1 -> timestamp = readAsLong(buf, wire);
                case 2 -> {
                    requireWire(wire, 2);
                    int len = (int) readVarint(buf);
                    byte[] metricBytes = new byte[len];
                    buf.get(metricBytes);
                    metrics.add(decodeMetric(metricBytes));
                }
                case 3 -> seq = readAsLong(buf, wire);
                default -> skip(buf, wire);
            }
        }
        return new Payload(timestamp, metrics, seq);
    }

    private static byte[] encodeMetric(Metric metric) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTag(out, 1, 2);
        byte[] name = metric.name().getBytes(StandardCharsets.UTF_8);
        writeVarint(out, name.length);
        out.writeBytes(name);

        writeTag(out, 4, 0);
        writeVarint(out, metric.dataType());

        Object value = metric.value();
        switch (metric.dataType()) {
            case DATATYPE_INT32 -> {
                writeTag(out, 10, 0);
                writeVarint(out, ((Number) value).longValue() & 0xFFFFFFFFL);
            }
            case DATATYPE_FLOAT -> {
                writeTag(out, 12, 5);
                writeFixed32(out, Float.floatToIntBits(((Number) value).floatValue()));
            }
            case DATATYPE_BOOLEAN -> {
                writeTag(out, 14, 0);
                writeVarint(out, Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) ? 1 : 0);
            }
            case DATATYPE_STRING -> {
                writeTag(out, 15, 2);
                byte[] text = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                writeVarint(out, text.length);
                out.writeBytes(text);
            }
            default -> throw new IllegalArgumentException("Unsupported Sparkplug datatype: " + metric.dataType());
        }
        return out.toByteArray();
    }

    private static Metric decodeMetric(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        String name = "";
        int dataType = DATATYPE_STRING;
        Object value = "";
        while (buf.hasRemaining()) {
            long key = readVarint(buf);
            int field = (int) (key >>> 3);
            int wire = (int) (key & 0x7);
            switch (field) {
                case 1 -> {
                    requireWire(wire, 2);
                    int len = (int) readVarint(buf);
                    byte[] raw = new byte[len];
                    buf.get(raw);
                    name = new String(raw, StandardCharsets.UTF_8);
                }
                case 4 -> dataType = (int) readAsLong(buf, wire);
                case 10 -> value = (int) readAsLong(buf, wire);
                case 11 -> value = readAsLong(buf, wire);
                case 12 -> {
                    requireWire(wire, 5);
                    value = Float.intBitsToFloat(readFixed32(buf));
                }
                case 13 -> {
                    requireWire(wire, 1);
                    value = Double.longBitsToDouble(readFixed64(buf));
                }
                case 14 -> value = readAsLong(buf, wire) != 0;
                case 15 -> {
                    requireWire(wire, 2);
                    int len = (int) readVarint(buf);
                    byte[] raw = new byte[len];
                    buf.get(raw);
                    value = new String(raw, StandardCharsets.UTF_8);
                }
                default -> skip(buf, wire);
            }
        }
        return new Metric(name, dataType, value);
    }

    static int inferDataType(Object value) {
        if (value instanceof Boolean) {
            return DATATYPE_BOOLEAN;
        }
        if (value instanceof Float || value instanceof Double) {
            return DATATYPE_FLOAT;
        }
        if (value instanceof Number) {
            return DATATYPE_INT32;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return DATATYPE_BOOLEAN;
        }
        try {
            Integer.parseInt(text.trim());
            return DATATYPE_INT32;
        } catch (NumberFormatException ignored) {
        }
        try {
            Float.parseFloat(text.trim());
            return DATATYPE_FLOAT;
        } catch (NumberFormatException ignored) {
        }
        return DATATYPE_STRING;
    }

    static Object coerce(int dataType, Object value) {
        return switch (dataType) {
            case DATATYPE_BOOLEAN -> value instanceof Boolean b
                    ? b
                    : Boolean.parseBoolean(String.valueOf(value));
            case DATATYPE_FLOAT -> value instanceof Number n
                    ? n.floatValue()
                    : Float.parseFloat(String.valueOf(value));
            case DATATYPE_INT32 -> value instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
            default -> String.valueOf(value);
        };
    }

    private static void writeTag(ByteArrayOutputStream out, int field, int wire) {
        writeVarint(out, ((long) field << 3) | wire);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    private static void writeFixed32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static long readVarint(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        while (buf.hasRemaining()) {
            byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 63) {
                throw new IllegalArgumentException("Varint too long");
            }
        }
        throw new IllegalArgumentException("Truncated varint");
    }

    private static long readAsLong(ByteBuffer buf, int wire) {
        requireWire(wire, 0);
        return readVarint(buf);
    }

    private static int readFixed32(ByteBuffer buf) {
        if (buf.remaining() < 4) {
            throw new IllegalArgumentException("Truncated fixed32");
        }
        int b0 = buf.get() & 0xFF;
        int b1 = buf.get() & 0xFF;
        int b2 = buf.get() & 0xFF;
        int b3 = buf.get() & 0xFF;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static long readFixed64(ByteBuffer buf) {
        if (buf.remaining() < 8) {
            throw new IllegalArgumentException("Truncated fixed64");
        }
        long lo = readFixed32(buf) & 0xFFFFFFFFL;
        long hi = readFixed32(buf) & 0xFFFFFFFFL;
        return lo | (hi << 32);
    }

    private static void skip(ByteBuffer buf, int wire) {
        switch (wire) {
            case 0 -> readVarint(buf);
            case 1 -> {
                if (buf.remaining() < 8) {
                    throw new IllegalArgumentException("Truncated fixed64");
                }
                buf.position(buf.position() + 8);
            }
            case 2 -> {
                int len = (int) readVarint(buf);
                if (buf.remaining() < len) {
                    throw new IllegalArgumentException("Truncated length-delimited");
                }
                buf.position(buf.position() + len);
            }
            case 5 -> {
                if (buf.remaining() < 4) {
                    throw new IllegalArgumentException("Truncated fixed32");
                }
                buf.position(buf.position() + 4);
            }
            default -> throw new IllegalArgumentException("Unsupported wire type " + wire);
        }
    }

    private static void requireWire(int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("Wire type " + actual + " != " + expected);
        }
    }
}
