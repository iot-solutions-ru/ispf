package com.ispf.driver.modbus;

/**
 * Decode Modbus register words for typed mappings and byte order.
 */
final class ModbusValueCodec {

    private ModbusValueCodec() {
    }

    static long combineWords(int[] words) {
        long raw = 0L;
        for (int word : words) {
            raw = (raw << 16) | (word & 0xFFFFL);
        }
        return raw;
    }

    /**
     * Rearrange wire bytes (high register first) into IEEE / integer host bit pattern.
     */
    static long rearrangeBits(int[] words, ModbusPoint.ByteOrder byteOrder) {
        byte[] wire = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) {
            wire[i * 2] = (byte) ((words[i] >> 8) & 0xFF);
            wire[i * 2 + 1] = (byte) (words[i] & 0xFF);
        }
        byte[] ordered = rearrangeBytes(wire, byteOrder);
        long bits = 0L;
        for (byte b : ordered) {
            bits = (bits << 8) | (b & 0xFFL);
        }
        return bits;
    }

    static byte[] rearrangeBytes(byte[] wire, ModbusPoint.ByteOrder byteOrder) {
        int n = wire.length;
        if (n == 2) {
            return switch (byteOrder) {
                case ABCD, CDAB -> wire.clone();
                case BADC, DCBA -> new byte[] { wire[1], wire[0] };
            };
        }
        if (n == 4) {
            byte a = wire[0];
            byte b = wire[1];
            byte c = wire[2];
            byte d = wire[3];
            return switch (byteOrder) {
                case ABCD -> new byte[] { a, b, c, d };
                case BADC -> new byte[] { b, a, d, c };
                case CDAB -> new byte[] { c, d, a, b };
                case DCBA -> new byte[] { d, c, b, a };
            };
        }
        if (n == 8) {
            byte a = wire[0];
            byte b = wire[1];
            byte c = wire[2];
            byte d = wire[3];
            byte e = wire[4];
            byte f = wire[5];
            byte g = wire[6];
            byte h = wire[7];
            return switch (byteOrder) {
                case ABCD -> new byte[] { a, b, c, d, e, f, g, h };
                case BADC -> new byte[] { b, a, d, c, f, e, h, g };
                case CDAB -> new byte[] { c, d, a, b, g, h, e, f };
                case DCBA -> new byte[] { h, g, f, e, d, c, b, a };
            };
        }
        throw new IllegalArgumentException("Unsupported width for byte order: " + n);
    }

    static double decodeValue(int[] words, ModbusPoint point) {
        return switch (point.dataType()) {
            case RAW -> (double) combineWords(words);
            case UINT16 -> (double) (words[0] & 0xFFFF);
            case INT16 -> (double) (short) words[0];
            case UINT32 -> {
                long bits = rearrangeBits(words, point.byteOrder()) & 0xFFFF_FFFFL;
                yield (double) bits;
            }
            case INT32 -> {
                int bits = (int) rearrangeBits(words, point.byteOrder());
                yield (double) bits;
            }
            case FLOAT32 -> {
                int bits = (int) rearrangeBits(words, point.byteOrder());
                yield Float.intBitsToFloat(bits);
            }
            case DOUBLE -> Double.longBitsToDouble(rearrangeBits(words, point.byteOrder()));
        };
    }
}
