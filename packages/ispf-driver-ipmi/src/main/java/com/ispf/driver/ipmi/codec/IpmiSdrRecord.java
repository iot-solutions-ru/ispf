package com.ispf.driver.ipmi.codec;

import com.ispf.driver.DriverException;

import java.nio.charset.StandardCharsets;

/**
 * Minimal SDR record parser for full and compact sensor records.
 */
public record IpmiSdrRecord(int recordType, int sensorNumber, String name, int m, int b, int rExp, int bExp) {

    private static final int FULL_SENSOR_RECORD = 0x01;
    private static final int COMPACT_SENSOR_RECORD = 0x02;

    public static IpmiSdrRecord parse(byte[] sdr) throws DriverException {
        if (sdr.length < 5) {
            throw new DriverException("IPMI SDR record too short");
        }
        int type = Byte.toUnsignedInt(sdr[3]);
        return switch (type) {
            case FULL_SENSOR_RECORD -> parseFull(sdr);
            case COMPACT_SENSOR_RECORD -> parseCompact(sdr);
            default -> throw new DriverException("Unsupported IPMI SDR record type 0x" + Integer.toHexString(type));
        };
    }

    public double convertReading(int rawReading) {
        if (recordType != FULL_SENSOR_RECORD) {
            return rawReading;
        }
        double linear = (m * rawReading + (b * Math.pow(10, bExp)));
        return linear * Math.pow(10, rExp);
    }

    private static IpmiSdrRecord parseFull(byte[] sdr) throws DriverException {
        if (sdr.length < 48) {
            throw new DriverException("IPMI full SDR record too short");
        }
        int m = twosComplement(sdr[24] | ((sdr[25] & 0xC0) << 2), 10);
        int b = twosComplement(sdr[26] | ((sdr[27] & 0xC0) << 2), 10);
        int exponents = Byte.toUnsignedInt(sdr[29]);
        int rExp = signedNibble(exponents >> 4);
        int bExp = signedNibble(exponents);
        return new IpmiSdrRecord(
                FULL_SENSOR_RECORD,
                Byte.toUnsignedInt(sdr[7]),
                sensorName(sdr, 47),
                m,
                b,
                rExp,
                bExp
        );
    }

    private static IpmiSdrRecord parseCompact(byte[] sdr) throws DriverException {
        if (sdr.length < 32) {
            throw new DriverException("IPMI compact SDR record too short");
        }
        return new IpmiSdrRecord(
                COMPACT_SENSOR_RECORD,
                Byte.toUnsignedInt(sdr[7]),
                sensorName(sdr, 31),
                1,
                0,
                0,
                0
        );
    }

    private static String sensorName(byte[] sdr, int descriptorOffset) throws DriverException {
        int descriptor = Byte.toUnsignedInt(sdr[descriptorOffset]);
        int encoding = descriptor & 0xC0;
        int length = descriptor & 0x3F;
        int start = descriptorOffset + 1;
        if (start + length > sdr.length) {
            throw new DriverException("IPMI SDR sensor name exceeds record length");
        }
        if (encoding != 0xC0) {
            throw new DriverException("Unsupported IPMI SDR sensor name encoding");
        }
        return new String(sdr, start, length, StandardCharsets.ISO_8859_1);
    }

    private static int signedNibble(int value) {
        int nibble = value & 0x0F;
        return (nibble & 0x08) == 0 ? nibble : nibble - 16;
    }

    private static int twosComplement(int value, int bits) {
        int sign = 1 << (bits - 1);
        int mask = (1 << bits) - 1;
        value &= mask;
        return (value & sign) == 0 ? value : value - (1 << bits);
    }
}
