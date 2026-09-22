package com.ispf.driver.genicam.codec;

/**
 * GigE Vision GVCP control-protocol helpers (discovery command header).
 * <p>
 * Discovery command starts {@code 42 01 00 02} (key 0x42, flags 0x01, command 0x0002).
 * Not a camera SDK / GenTL producer.
 */
public final class GenicamCodec {

    public static final int KEY_COMMAND = 0x42;
    public static final int FLAG_ACK_REQUIRED = 0x01;
    public static final int CMD_DISCOVERY = 0x0002;

    private GenicamCodec() {
    }

    /**
     * Encodes an 8-byte GVCP discovery command: key, flag, command, length 0, request id.
     */
    public static byte[] encodeDiscoveryCommand(int requestId) {
        int id = requestId & 0xFFFF;
        return new byte[] {
                (byte) KEY_COMMAND,
                (byte) FLAG_ACK_REQUIRED,
                0x00,
                0x02,
                0x00,
                0x00,
                (byte) ((id >> 8) & 0xFF),
                (byte) (id & 0xFF)
        };
    }
}
