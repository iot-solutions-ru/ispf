package com.ispf.driver.secsgem.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * SEMI E37 HSMS framing over TCP.
 * <p>
 * After the 4-byte big-endian length come ten header octets: sessionId (uint16),
 * header byte 2 (stream in the low 7 bits, W-bit in bit 7), function, PType, SType,
 * and system bytes (uint32). Length counts those ten octets plus any SECS-II body.
 * Data messages use PType 0 and SType 0.
 */
public final class HsmsCodec {

    private HsmsCodec() {
    }

    public static byte[] encodeControl(int sessionId, int systemBytes, int sType) {
        return encode(sessionId, 0, 0, false, SecsGemTypes.PTYPE_SECS_II, sType, systemBytes, new byte[0]);
    }

    public static byte[] encodeSelectReq(int systemBytes) {
        return encodeControl(SecsGemTypes.SESSION_ID_SELECT, systemBytes, SecsGemTypes.STYPE_SELECT_REQ);
    }

    public static byte[] encodeData(
            int sessionId,
            int stream,
            int function,
            boolean waitBit,
            int systemBytes,
            byte[] secs2Body
    ) {
        return encode(
                sessionId,
                stream,
                function,
                waitBit,
                SecsGemTypes.PTYPE_SECS_II,
                SecsGemTypes.STYPE_DATA,
                systemBytes,
                secs2Body == null ? new byte[0] : secs2Body
        );
    }

    public static byte[] encode(
            int sessionId,
            int stream,
            int function,
            boolean waitBit,
            int pType,
            int sType,
            int systemBytes,
            byte[] body
    ) {
        byte[] payload = body == null ? new byte[0] : body;
        byte[] message = new byte[14 + payload.length];
        int length = 10 + payload.length;
        message[0] = (byte) ((length >>> 24) & 0xFF);
        message[1] = (byte) ((length >>> 16) & 0xFF);
        message[2] = (byte) ((length >>> 8) & 0xFF);
        message[3] = (byte) (length & 0xFF);
        message[4] = (byte) ((sessionId >>> 8) & 0xFF);
        message[5] = (byte) (sessionId & 0xFF);
        int headerByte2 = stream & 0x7F;
        if (waitBit) {
            headerByte2 |= 0x80;
        }
        message[6] = (byte) headerByte2;
        message[7] = (byte) (function & 0xFF);
        message[8] = (byte) (pType & 0xFF);
        message[9] = (byte) (sType & 0xFF);
        message[10] = (byte) ((systemBytes >>> 24) & 0xFF);
        message[11] = (byte) ((systemBytes >>> 16) & 0xFF);
        message[12] = (byte) ((systemBytes >>> 8) & 0xFF);
        message[13] = (byte) (systemBytes & 0xFF);
        System.arraycopy(payload, 0, message, 14, payload.length);
        return message;
    }

    public static HsmsMessage parse(byte[] frame) throws IOException {
        if (frame == null || frame.length < 14) {
            throw new IOException("HSMS frame too short");
        }
        int length = ByteBuffer.wrap(frame, 0, 4).getInt();
        if (length < 10 || frame.length < 4 + length) {
            throw new IOException("HSMS truncated (length=" + length + ")");
        }
        int sessionId = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        int headerByte2 = frame[6] & 0xFF;
        boolean waitBit = (headerByte2 & 0x80) != 0;
        int stream = headerByte2 & 0x7F;
        int function = frame[7] & 0xFF;
        int pType = frame[8] & 0xFF;
        int sType = frame[9] & 0xFF;
        int systemBytes = ByteBuffer.wrap(frame, 10, 4).getInt();
        byte[] body = Arrays.copyOfRange(frame, 14, 4 + length);
        return new HsmsMessage(sessionId, stream, function, waitBit, pType, sType, systemBytes, body);
    }

    /**
     * Parsed HSMS header plus optional SECS-II body.
     */
    public static final class HsmsMessage {
        private final int sessionId;
        private final int stream;
        private final int function;
        private final boolean waitBit;
        private final int pType;
        private final int sType;
        private final int systemBytes;
        private final byte[] body;

        HsmsMessage(
                int sessionId,
                int stream,
                int function,
                boolean waitBit,
                int pType,
                int sType,
                int systemBytes,
                byte[] body
        ) {
            this.sessionId = sessionId;
            this.stream = stream;
            this.function = function;
            this.waitBit = waitBit;
            this.pType = pType;
            this.sType = sType;
            this.systemBytes = systemBytes;
            this.body = body == null ? new byte[0] : body.clone();
        }

        public int sessionId() {
            return sessionId;
        }

        public int stream() {
            return stream;
        }

        public int function() {
            return function;
        }

        public boolean waitBit() {
            return waitBit;
        }

        public int pType() {
            return pType;
        }

        public int sType() {
            return sType;
        }

        public int systemBytes() {
            return systemBytes;
        }

        public byte[] body() {
            return body.clone();
        }

        public boolean isData() {
            return sType == SecsGemTypes.STYPE_DATA && pType == SecsGemTypes.PTYPE_SECS_II;
        }

        public boolean isSelectRsp() {
            return sType == SecsGemTypes.STYPE_SELECT_RSP;
        }
    }
}
