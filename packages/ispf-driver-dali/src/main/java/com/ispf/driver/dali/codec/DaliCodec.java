package com.ispf.driver.dali.codec;

/**
 * IEC 62386 DALI forward-frame helpers (two-byte command or DAPC on the wire).
 * <p>
 * Short-address command: {@code (S << 1) | 1}. Short-address DAPC: {@code S << 1}.
 * OFF (command 0x00) to short address 0 is exactly {@code 01 00}.
 */
public final class DaliCodec {

    public static final int CMD_OFF = 0x00;
    public static final int CMD_QUERY_ACTUAL_LEVEL = 0xA0;

    private DaliCodec() {
    }

    public static int shortAddressCommandByte(int shortAddress) {
        return ((shortAddress & 0x3F) << 1) | 1;
    }

    public static int shortAddressDapcByte(int shortAddress) {
        return (shortAddress & 0x3F) << 1;
    }

    public static int groupCommandByte(int group) {
        return 0x80 | ((group & 0x0F) << 1) | 1;
    }

    public static int groupDapcByte(int group) {
        return 0x80 | ((group & 0x0F) << 1);
    }

    public static int broadcastCommandByte() {
        return 0xFF;
    }

    public static int broadcastDapcByte() {
        return 0xFE;
    }

    /** OFF to short address 0 — literal {@code 01 00}. */
    public static byte[] buildOffShortAddress0() {
        return new byte[] {
                (byte) shortAddressCommandByte(0),
                (byte) CMD_OFF
        };
    }

    public static byte[] buildCommand(int addressByte, int command) {
        return new byte[] { (byte) (addressByte & 0xFF), (byte) (command & 0xFF) };
    }

    public static byte[] buildDapc(int addressByte, int level) {
        return new byte[] { (byte) (addressByte & 0xFF), (byte) (level & 0xFF) };
    }
}
