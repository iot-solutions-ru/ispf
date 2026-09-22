package com.ispf.driver.enocean.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * EnOcean Serial Protocol 3 (ESP3) framing for serial-over-TCP.
 * <p>
 * Packet layout: sync {@code 0x55}, data length (uint16 BE), optional length, packet type,
 * CRC8 of the four header fields, data, optional data, CRC8 of data+optional.
 * CRC8 uses polynomial {@code 0x07} ({@code x^8+x^2+x+1}), init {@code 0}, MSB-first —
 * the EnOcean Alliance ESP3 / ISO-style CRC used by ESP3 gateways.
 * <p>
 * Clean-room Apache-2.0, JDK only — not EnOcean Alliance SDK / TCM radio PHY.
 */
public final class Esp3Codec {

    public static final int SYNC = 0x55;
    public static final int TYPE_RADIO = 0x01;
    public static final int TYPE_RESPONSE = 0x02;
    public static final int TYPE_COMMON_COMMAND = 0x05;

    /** COMMON_COMMAND: read controller version. */
    public static final int CO_RD_VERSION = 0x03;

    public static final int RET_OK = 0x00;

    private Esp3Codec() {
    }

    /**
     * Published ESP3 COMMON_COMMAND {@link #CO_RD_VERSION} request as a handwritten literal:
     * {@code 55 00 01 00 05 70 03 09}.
     */
    public static byte[] coRdVersionRequestLiteral() {
        return new byte[] {
                (byte) 0x55,
                0x00, 0x01,
                0x00,
                0x05,
                0x70,
                0x03,
                0x09
        };
    }

    public static byte[] encode(int packetType, byte[] data, byte[] optional) {
        byte[] body = data == null ? new byte[0] : data;
        byte[] opt = optional == null ? new byte[0] : optional;
        if (body.length > 0xFFFF) {
            throw new IllegalArgumentException("ESP3 data length exceeds uint16");
        }
        if (opt.length > 0xFF) {
            throw new IllegalArgumentException("ESP3 optional length exceeds uint8");
        }
        byte[] headerFields = new byte[] {
                (byte) ((body.length >> 8) & 0xFF),
                (byte) (body.length & 0xFF),
                (byte) (opt.length & 0xFF),
                (byte) (packetType & 0xFF)
        };
        int headerCrc = crc8(headerFields);
        int dataCrc = crc8(concat(body, opt));
        byte[] frame = new byte[6 + body.length + opt.length + 1];
        frame[0] = (byte) SYNC;
        frame[1] = headerFields[0];
        frame[2] = headerFields[1];
        frame[3] = headerFields[2];
        frame[4] = headerFields[3];
        frame[5] = (byte) headerCrc;
        System.arraycopy(body, 0, frame, 6, body.length);
        System.arraycopy(opt, 0, frame, 6 + body.length, opt.length);
        frame[frame.length - 1] = (byte) dataCrc;
        return frame;
    }

    public static byte[] encodeRadio(byte[] deviceId, byte[] payload) {
        byte[] id = requireDeviceId(deviceId);
        byte[] data = payload == null ? new byte[0] : payload;
        byte[] body = new byte[4 + data.length];
        System.arraycopy(id, 0, body, 0, 4);
        System.arraycopy(data, 0, body, 4, data.length);
        return encode(TYPE_RADIO, body, new byte[0]);
    }

    public static byte[] encodeResponseOk() {
        return encode(TYPE_RESPONSE, new byte[] { (byte) RET_OK }, new byte[0]);
    }

    public static Esp3Packet decode(byte[] frame) {
        if (frame == null || frame.length < 7) {
            throw new IllegalArgumentException("ESP3 frame too short");
        }
        if ((frame[0] & 0xFF) != SYNC) {
            throw new IllegalArgumentException("ESP3 sync must be 0x55");
        }
        int dataLength = ((frame[1] & 0xFF) << 8) | (frame[2] & 0xFF);
        int optionalLength = frame[3] & 0xFF;
        int packetType = frame[4] & 0xFF;
        int headerCrc = frame[5] & 0xFF;
        int expectedHeaderCrc = crc8(new byte[] {
                frame[1], frame[2], frame[3], frame[4]
        });
        if (headerCrc != expectedHeaderCrc) {
            throw new IllegalArgumentException("ESP3 header CRC mismatch");
        }
        int total = 6 + dataLength + optionalLength + 1;
        if (frame.length < total) {
            throw new IllegalArgumentException("ESP3 frame truncated");
        }
        byte[] data = Arrays.copyOfRange(frame, 6, 6 + dataLength);
        byte[] optional = Arrays.copyOfRange(frame, 6 + dataLength, 6 + dataLength + optionalLength);
        int dataCrc = frame[6 + dataLength + optionalLength] & 0xFF;
        int expectedDataCrc = crc8(concat(data, optional));
        if (dataCrc != expectedDataCrc) {
            throw new IllegalArgumentException("ESP3 data CRC mismatch");
        }
        return new Esp3Packet(packetType, data, optional);
    }

    public static Esp3Packet readPacket(InputStream in) throws IOException {
        int sync = in.read();
        if (sync < 0) {
            throw new IOException("EOF before ESP3 sync");
        }
        while (sync != SYNC) {
            sync = in.read();
            if (sync < 0) {
                throw new IOException("EOF seeking ESP3 sync");
            }
        }
        byte[] header = in.readNBytes(5);
        if (header.length < 5) {
            throw new IOException("EOF before ESP3 header");
        }
        int dataLength = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int optionalLength = header[2] & 0xFF;
        byte[] rest = in.readNBytes(dataLength + optionalLength + 1);
        if (rest.length < dataLength + optionalLength + 1) {
            throw new IOException("EOF before ESP3 payload/CRC");
        }
        byte[] frame = new byte[6 + rest.length];
        frame[0] = (byte) SYNC;
        System.arraycopy(header, 0, frame, 1, 5);
        System.arraycopy(rest, 0, frame, 6, rest.length);
        return decode(frame);
    }

    public static void writePacket(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    /**
     * Published EnOcean ESP3 CRC8 lookup (poly {@code 0x07}, init {@code 0}, MSB-first).
     */
    private static final int[] CRC8_TABLE = {
            0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15, 0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A, 0x2D,
            0x70, 0x77, 0x7E, 0x79, 0x6C, 0x6B, 0x62, 0x65, 0x48, 0x4F, 0x46, 0x41, 0x54, 0x53, 0x5A, 0x5D,
            0xE0, 0xE7, 0xEE, 0xE9, 0xFC, 0xFB, 0xF2, 0xF5, 0xD8, 0xDF, 0xD6, 0xD1, 0xC4, 0xC3, 0xCA, 0xCD,
            0x90, 0x97, 0x9E, 0x99, 0x8C, 0x8B, 0x82, 0x85, 0xA8, 0xAF, 0xA6, 0xA1, 0xB4, 0xB3, 0xBA, 0xBD,
            0xC7, 0xC0, 0xC9, 0xCE, 0xDB, 0xDC, 0xD5, 0xD2, 0xFF, 0xF8, 0xF1, 0xF6, 0xE3, 0xE4, 0xED, 0xEA,
            0xB7, 0xB0, 0xB9, 0xBE, 0xAB, 0xAC, 0xA5, 0xA2, 0x8F, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9D, 0x9A,
            0x27, 0x20, 0x29, 0x2E, 0x3B, 0x3C, 0x35, 0x32, 0x1F, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0D, 0x0A,
            0x57, 0x50, 0x59, 0x5E, 0x4B, 0x4C, 0x45, 0x42, 0x6F, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7D, 0x7A,
            0x89, 0x8E, 0x87, 0x80, 0x95, 0x92, 0x9B, 0x9C, 0xB1, 0xB6, 0xBF, 0xB8, 0xAD, 0xAA, 0xA3, 0xA4,
            0xF9, 0xFE, 0xF7, 0xF0, 0xE5, 0xE2, 0xEB, 0xEC, 0xC1, 0xC6, 0xCF, 0xC8, 0xDD, 0xDA, 0xD3, 0xD4,
            0x69, 0x6E, 0x67, 0x60, 0x75, 0x72, 0x7B, 0x7C, 0x51, 0x56, 0x5F, 0x58, 0x4D, 0x4A, 0x43, 0x44,
            0x19, 0x1E, 0x17, 0x10, 0x05, 0x02, 0x0B, 0x0C, 0x21, 0x26, 0x2F, 0x28, 0x3D, 0x3A, 0x33, 0x34,
            0x4E, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5C, 0x5B, 0x76, 0x71, 0x78, 0x7F, 0x6A, 0x6D, 0x64, 0x63,
            0x3E, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2C, 0x2B, 0x06, 0x01, 0x08, 0x0F, 0x1A, 0x1D, 0x14, 0x13,
            0xAE, 0xA9, 0xA0, 0xA7, 0xB2, 0xB5, 0xBC, 0xBB, 0x96, 0x91, 0x98, 0x9F, 0x8A, 0x8D, 0x84, 0x83,
            0xDE, 0xD9, 0xD0, 0xD7, 0xC2, 0xC5, 0xCC, 0xCB, 0xE6, 0xE1, 0xE8, 0xEF, 0xFA, 0xFD, 0xF4, 0xF3
    };

    /** ESP3 CRC8 via the published EnOcean Alliance lookup table. */
    public static int crc8(byte[] data) {
        int crc = 0;
        if (data == null) {
            return 0;
        }
        for (byte value : data) {
            crc = CRC8_TABLE[(crc ^ (value & 0xFF)) & 0xFF];
        }
        return crc;
    }

    public static byte[] requireDeviceId(byte[] deviceId) {
        if (deviceId == null || deviceId.length != 4) {
            throw new IllegalArgumentException("EnOcean device id must be 4 bytes");
        }
        return deviceId;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] a = left == null ? new byte[0] : left;
        byte[] b = right == null ? new byte[0] : right;
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
