package com.ispf.driver.wisun.codec;

import java.io.IOException;

/**
 * CoAP RFC 7252 header subset for Wi-SUN border-router UDP (CON GET / ACK 2.05).
 * <p>
 * Not Wi-SUN FAN PHY / FAN stack. Clean-room Apache-2.0, JDK only.
 */
public final class WisunCoapCodec {

    public static final int VERSION = 1;
    public static final int TYPE_CON = 0;
    public static final int TYPE_ACK = 2;
    public static final int CODE_GET = 1;
    public static final int CODE_CONTENT = 69; // 2.05

    private WisunCoapCodec() {
    }

    /**
     * CON GET with empty token for the given message id.
     * MID 1 yields {@code 40 01 00 01}.
     */
    public static byte[] encodeConGetNoToken(int messageId) {
        int mid = messageId & 0xFFFF;
        return new byte[]{
                (byte) ((VERSION << 6) | (TYPE_CON << 4) | 0),
                (byte) CODE_GET,
                (byte) ((mid >> 8) & 0xFF),
                (byte) (mid & 0xFF)
        };
    }

    /**
     * ACK 2.05 Content with empty token and no payload for the given message id.
     * MID 1 yields {@code 60 45 00 01}.
     */
    public static byte[] encodeAckContentNoToken(int messageId) {
        int mid = messageId & 0xFFFF;
        return new byte[]{
                (byte) ((VERSION << 6) | (TYPE_ACK << 4) | 0),
                (byte) CODE_CONTENT,
                (byte) ((mid >> 8) & 0xFF),
                (byte) (mid & 0xFF)
        };
    }

    public static int parseAckContentMessageId(byte[] frame) throws IOException {
        if (frame == null || frame.length < 4) {
            throw new IOException("Short CoAP response");
        }
        int ver = (frame[0] >> 6) & 0x03;
        int type = (frame[0] >> 4) & 0x03;
        int tkl = frame[0] & 0x0F;
        if (ver != VERSION) {
            throw new IOException("Unsupported CoAP version " + ver);
        }
        if (type != TYPE_ACK) {
            throw new IOException("Expected CoAP ACK, got type=" + type);
        }
        if (tkl != 0) {
            throw new IOException("Expected empty CoAP token, tkl=" + tkl);
        }
        int code = frame[1] & 0xFF;
        if (code != CODE_CONTENT) {
            throw new IOException("Expected CoAP 2.05 Content, got code=" + code);
        }
        return ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
    }
}
