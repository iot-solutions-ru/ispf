package com.ispf.driver.secsgem.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal clean-room SECS-II item codec for the GEM-lab subset (Apache-2.0).
 * <p>
 * Supports List, ASCII, U1, U2, U4, Boolean, and F4 only — not a full SEMI E5 stack.
 */
public final class Secs2LabCodec {

    public static final int FORMAT_LIST = 0x00;
    public static final int FORMAT_BOOLEAN = 0x24;
    public static final int FORMAT_ASCII = 0x40;
    public static final int FORMAT_U1 = 0xA4;
    public static final int FORMAT_U2 = 0xA8;
    public static final int FORMAT_U4 = 0xB0;
    public static final int FORMAT_F4 = 0x80;

    private Secs2LabCodec() {
    }

    public static byte[] encodeList(List<byte[]> items) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] item : items) {
            body.writeBytes(item);
        }
        return wrap(FORMAT_LIST, items.size(), body.toByteArray());
    }

    public static byte[] encodeAscii(String text) {
        byte[] data = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
        return wrap(FORMAT_ASCII, data.length, data);
    }

    public static byte[] encodeU1(int value) {
        return wrap(FORMAT_U1, 1, new byte[] { (byte) (value & 0xFF) });
    }

    public static byte[] encodeU2(int value) {
        return wrap(FORMAT_U2, 2, new byte[] {
                (byte) ((value >>> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static byte[] encodeU4(long value) {
        return wrap(FORMAT_U4, 4, new byte[] {
                (byte) ((value >>> 24) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) (value & 0xFF)
        });
    }

    public static byte[] encodeBoolean(boolean value) {
        return wrap(FORMAT_BOOLEAN, 1, new byte[] { (byte) (value ? 1 : 0) });
    }

    public static byte[] encodeF4(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return wrap(FORMAT_F4, 4, buffer.array());
    }

    public static byte[] encodeEmptyList() {
        return wrap(FORMAT_LIST, 0, new byte[0]);
    }

    private static byte[] wrap(int formatCode, int length, byte[] data) {
        int lengthBytes = lengthBytesFor(length);
        byte[] out = new byte[1 + lengthBytes + data.length];
        out[0] = (byte) (formatCode | lengthBytes);
        for (int i = 0; i < lengthBytes; i++) {
            out[1 + i] = (byte) ((length >>> (8 * (lengthBytes - 1 - i))) & 0xFF);
        }
        System.arraycopy(data, 0, out, 1 + lengthBytes, data.length);
        return out;
    }

    private static int lengthBytesFor(int length) {
        if (length <= 0xFF) {
            return 1;
        }
        if (length <= 0xFFFF) {
            return 2;
        }
        return 3;
    }

    public static Item parse(byte[] data) throws IOException {
        Parsed parsed = parseAt(data, 0);
        return parsed.item();
    }

    public static Parsed parseAt(byte[] data, int offset) throws IOException {
        if (offset >= data.length) {
            throw new IOException("SECS-II truncated at offset " + offset);
        }
        int formatByte = data[offset] & 0xFF;
        int format = formatByte & 0xFC;
        int lengthBytes = formatByte & 0x03;
        if (lengthBytes == 0) {
            // lab: treat 0 length-bytes as empty item with no payload length field
            return new Parsed(new Item(format, List.of(), new byte[0], null, 0L, 0.0, false), offset + 1);
        }
        if (offset + 1 + lengthBytes > data.length) {
            throw new IOException("SECS-II length truncated");
        }
        int length = 0;
        for (int i = 0; i < lengthBytes; i++) {
            length = (length << 8) | (data[offset + 1 + i] & 0xFF);
        }
        int dataStart = offset + 1 + lengthBytes;
        if (format == FORMAT_LIST) {
            List<Item> children = new ArrayList<>(length);
            int cursor = dataStart;
            for (int i = 0; i < length; i++) {
                Parsed child = parseAt(data, cursor);
                children.add(child.item());
                cursor = child.next();
            }
            return new Parsed(new Item(format, children, new byte[0], null, 0L, 0.0, false), cursor);
        }
        if (dataStart + length > data.length) {
            throw new IOException("SECS-II payload truncated");
        }
        byte[] payload = new byte[length];
        System.arraycopy(data, dataStart, payload, 0, length);
        return switch (format) {
            case FORMAT_ASCII -> new Parsed(
                    new Item(format, List.of(), payload, new String(payload, StandardCharsets.US_ASCII), 0L, 0.0, false),
                    dataStart + length);
            case FORMAT_BOOLEAN -> {
                boolean value = length > 0 && payload[0] != 0;
                yield new Parsed(new Item(format, List.of(), payload, null, value ? 1L : 0L, 0.0, value),
                        dataStart + length);
            }
            case FORMAT_U1 -> {
                long value = length > 0 ? payload[0] & 0xFFL : 0L;
                yield new Parsed(new Item(format, List.of(), payload, null, value, value, false), dataStart + length);
            }
            case FORMAT_U2 -> {
                long value = length >= 2
                        ? ((payload[0] & 0xFFL) << 8) | (payload[1] & 0xFFL)
                        : 0L;
                yield new Parsed(new Item(format, List.of(), payload, null, value, value, false), dataStart + length);
            }
            case FORMAT_U4 -> {
                long value = 0L;
                for (int i = 0; i < Math.min(4, length); i++) {
                    value = (value << 8) | (payload[i] & 0xFFL);
                }
                yield new Parsed(new Item(format, List.of(), payload, null, value, value, false), dataStart + length);
            }
            case FORMAT_F4 -> {
                float value = length >= 4 ? ByteBuffer.wrap(payload, 0, 4).getFloat() : 0f;
                yield new Parsed(new Item(format, List.of(), payload, null, (long) value, value, false),
                        dataStart + length);
            }
            default -> new Parsed(new Item(format, List.of(), payload, null, 0L, 0.0, false), dataStart + length);
        };
    }

    public record Item(
            int format,
            List<Item> children,
            byte[] raw,
            String ascii,
            long unsigned,
            double numeric,
            boolean bool
    ) {
        public boolean isList() {
            return format == FORMAT_LIST;
        }
    }

    public record Parsed(Item item, int next) {
    }
}
