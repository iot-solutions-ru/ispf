package com.ispf.driver.secsgem.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Clean-room HSMS (SEMI E37) framing for the SECS/GEM lab subset (Apache-2.0).
 * <p>
 * <strong>HSMS-lab only</strong> — Select.req/rsp + Data Message. Not SECS-I serial,
 * not a commercial GEM package.
 */
public final class HsmsLabCodec {

    private HsmsLabCodec() {
    }

    public static byte[] encodeControl(int sessionId, int systemBytes, int sType) {
        return encode(sessionId, 0, 0, false, SecsGemLabTypes.PTYPE_SECS_II, sType, systemBytes, new byte[0]);
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
                SecsGemLabTypes.PTYPE_SECS_II,
                SecsGemLabTypes.STYPE_DATA,
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
        byte[] message = new byte[14 + body.length];
        int length = 10 + body.length;
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
        System.arraycopy(body, 0, message, 14, body.length);
        return message;
    }

    public static HsmsMessage parse(byte[] frame) throws IOException {
        if (frame.length < 14) {
            throw new IOException("HSMS-lab frame too short");
        }
        int length = ByteBuffer.wrap(frame, 0, 4).getInt();
        if (length < 10 || frame.length < 4 + length) {
            throw new IOException("HSMS-lab truncated (length=" + length + ")");
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

    public record HsmsMessage(
            int sessionId,
            int stream,
            int function,
            boolean waitBit,
            int pType,
            int sType,
            int systemBytes,
            byte[] body
    ) {
        public boolean isData() {
            return sType == SecsGemLabTypes.STYPE_DATA;
        }

        public boolean isSelectRsp() {
            return sType == SecsGemLabTypes.STYPE_SELECT_RSP;
        }
    }
}
