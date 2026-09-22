package com.ispf.driver.thread.codec;

/**
 * OpenThread Spinel over HDLC framing (RFC 1662-style FCS).
 * <p>
 * CMD_RESET is header {@code 0x80}, command {@code 0x01}, CRC-16/ISO-HDLC of {@code 80 01}
 * little-endian, framed with {@code 0x7E} flags. Not 802.15.4 radio silicon.
 * Clean-room Apache-2.0, JDK only.
 */
public final class SpinelHdlcCodec {

    public static final byte FLAG = 0x7E;
    public static final int HEADER_RESET = 0x80;
    public static final int CMD_RESET = 0x01;

    private SpinelHdlcCodec() {
    }

    /**
     * Encodes Spinel CMD_RESET: {@code 7E 80 01 02 92 7E}.
     */
    public static byte[] encodeCmdReset() {
        byte[] payload = new byte[]{(byte) HEADER_RESET, (byte) CMD_RESET};
        int crc = crc16IsoHdlc(payload);
        return new byte[]{
                FLAG,
                payload[0],
                payload[1],
                (byte) (crc & 0xFF),
                (byte) ((crc >> 8) & 0xFF),
                FLAG
        };
    }

    /**
     * CRC-16/ISO-HDLC (init {@code 0xFFFF}, reflected poly {@code 0x8408}, xorout {@code 0xFFFF}).
     * Bit-loop only — no lookup table.
     */
    public static int crc16IsoHdlc(byte[] data) {
        int crc = 0xFFFF;
        for (byte value : data) {
            crc ^= (value & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc >>>= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }
}
