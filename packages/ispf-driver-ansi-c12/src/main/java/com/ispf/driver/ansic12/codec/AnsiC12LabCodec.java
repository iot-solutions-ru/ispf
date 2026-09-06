package com.ispf.driver.ansic12.codec;

/**
 * Clean-room ANSI C12.18-inspired lab framing (Apache-2.0).
 * <p>
 * <strong>Lab subset — not a certified ANSI C12.18 optical probe or C12.22 network relay.</strong>
 * Packets use a simplified envelope suitable for TCP loopback on port 1153:
 * {@code STP(0xEE) | identity | ctrl | length(2 BE) | payload | CRC16-IBM}.
 * Services implemented: logon, read standard table, optional write standard table.
 * No meter-vendor SDKs.
 */
public final class AnsiC12LabCodec {

    public static final byte STP = (byte) 0xEE;

    public static final byte CTRL_REQUEST = 0x00;
    public static final byte CTRL_RESPONSE = 0x20;

    public static final byte SVC_LOGON = 0x50;
    public static final byte SVC_LOGOFF = 0x52;
    public static final byte SVC_READ_TABLE = 0x30;
    public static final byte SVC_WRITE_TABLE = 0x40;

    public static final byte ACK_OK = 0x00;
    public static final byte ACK_ERR = 0x01;

    private AnsiC12LabCodec() {
    }

    public static byte[] encodeRequest(byte service, byte[] payload) {
        return encode(CTRL_REQUEST, service, payload == null ? new byte[0] : payload);
    }

    public static byte[] encodeResponse(byte service, byte ack, byte[] payload) {
        byte[] body = new byte[1 + (payload == null ? 0 : payload.length)];
        body[0] = ack;
        if (payload != null && payload.length > 0) {
            System.arraycopy(payload, 0, body, 1, payload.length);
        }
        return encode(CTRL_RESPONSE, service, body);
    }

    public static byte[] encode(byte ctrl, byte service, byte[] payload) {
        int length = 1 + payload.length; // service + payload
        byte[] frame = new byte[1 + 1 + 1 + 2 + length + 2];
        int i = 0;
        frame[i++] = STP;
        frame[i++] = 0x00; // identity / reserved
        frame[i++] = ctrl;
        frame[i++] = (byte) ((length >>> 8) & 0xFF);
        frame[i++] = (byte) (length & 0xFF);
        frame[i++] = service;
        System.arraycopy(payload, 0, frame, i, payload.length);
        i += payload.length;
        int crc = crc16Ibm(frame, 0, i);
        frame[i++] = (byte) (crc & 0xFF);
        frame[i] = (byte) ((crc >>> 8) & 0xFF);
        return frame;
    }

    public static ParsedFrame parse(byte[] frame) throws IllegalArgumentException {
        if (frame.length < 8 || frame[0] != STP) {
            throw new IllegalArgumentException("ANSI C12-lab frame missing STP");
        }
        byte identity = frame[1];
        byte ctrl = frame[2];
        int length = ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
        if (frame.length < 5 + length + 2) {
            throw new IllegalArgumentException("ANSI C12-lab frame truncated");
        }
        int crcOffset = 5 + length;
        int expected = crc16Ibm(frame, 0, crcOffset);
        int actual = (frame[crcOffset] & 0xFF) | ((frame[crcOffset + 1] & 0xFF) << 8);
        if (expected != actual) {
            throw new IllegalArgumentException("ANSI C12-lab CRC mismatch");
        }
        byte service = frame[5];
        byte[] payload = new byte[length - 1];
        System.arraycopy(frame, 6, payload, 0, payload.length);
        return new ParsedFrame(identity, ctrl, service, payload);
    }

    public static byte[] logonPayload(String user, String password) {
        byte[] userBytes = padAscii(user, 10);
        byte[] passBytes = padAscii(password, 10);
        byte[] payload = new byte[20];
        System.arraycopy(userBytes, 0, payload, 0, 10);
        System.arraycopy(passBytes, 0, payload, 10, 10);
        return payload;
    }

    public static byte[] readTablePayload(int tableId) {
        return new byte[] {
                (byte) ((tableId >>> 8) & 0xFF),
                (byte) (tableId & 0xFF)
        };
    }

    public static byte[] writeTablePayload(int tableId, byte[] data) {
        byte[] payload = new byte[2 + data.length];
        payload[0] = (byte) ((tableId >>> 8) & 0xFF);
        payload[1] = (byte) (tableId & 0xFF);
        System.arraycopy(data, 0, payload, 2, data.length);
        return payload;
    }

    public static int tableIdFromPayload(byte[] payload) {
        if (payload.length < 2) {
            throw new IllegalArgumentException("Table payload too short");
        }
        return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
    }

    private static byte[] padAscii(String value, int size) {
        byte[] out = new byte[size];
        if (value == null) {
            return out;
        }
        byte[] raw = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, out, 0, Math.min(size, raw.length));
        return out;
    }

    /**
     * CRC-16/IBM (poly 0xA001, init 0xFFFF) — common for C12.18-style envelopes.
     */
    public static int crc16Ibm(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public record ParsedFrame(byte identity, byte ctrl, byte service, byte[] payload) {
        public boolean isResponse() {
            return (ctrl & CTRL_RESPONSE) != 0;
        }
    }
}
