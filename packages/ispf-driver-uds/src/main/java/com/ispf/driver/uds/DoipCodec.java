package com.ispf.driver.uds;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * ISO 13400-2 DoIP framing helpers for diagnostic messaging over TCP.
 * <p>
 * Header: protocol version {@code 0x02}, inverse {@code 0xFD}, payload type
 * (uint16 BE), payload length (uint32 BE), then payload bytes.
 */
final class DoipCodec {

    static final byte PROTOCOL_VERSION = 0x02;
    static final byte PROTOCOL_VERSION_INVERSE = (byte) 0xFD;

    static final int PAYLOAD_ROUTING_ACTIVATION_REQUEST = 0x0005;
    static final int PAYLOAD_ROUTING_ACTIVATION_RESPONSE = 0x0006;
    static final int PAYLOAD_DIAGNOSTIC_MESSAGE = 0x8001;

    /** Routing activation request: source address + activation type + 4 reserved bytes. */
    static final int ROUTING_ACTIVATION_REQUEST_LENGTH = 7;

    private DoipCodec() {
    }

    /**
     * Build a routing activation request frame for the given source address and
     * activation type {@code 0} with four reserved zero bytes.
     */
    static byte[] buildRoutingActivationRequest(int sourceAddress) {
        byte[] payload = new byte[ROUTING_ACTIVATION_REQUEST_LENGTH];
        payload[0] = (byte) ((sourceAddress >> 8) & 0xFF);
        payload[1] = (byte) (sourceAddress & 0xFF);
        payload[2] = 0x00; // activation type
        // bytes 3..6 remain reserved zeros
        return encodeFrame(PAYLOAD_ROUTING_ACTIVATION_REQUEST, payload);
    }

    static byte[] encodeFrame(int payloadType, byte[] payload) {
        byte[] body = payload == null ? new byte[0] : payload;
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + body.length);
        out.write(PROTOCOL_VERSION & 0xFF);
        out.write(PROTOCOL_VERSION_INVERSE & 0xFF);
        out.write((payloadType >> 8) & 0xFF);
        out.write(payloadType & 0xFF);
        out.write((body.length >> 24) & 0xFF);
        out.write((body.length >> 16) & 0xFF);
        out.write((body.length >> 8) & 0xFF);
        out.write(body.length & 0xFF);
        out.writeBytes(body);
        return out.toByteArray();
    }

    static void writeFrame(OutputStream out, int payloadType, byte[] payload) throws IOException {
        out.write(encodeFrame(payloadType, payload));
        out.flush();
    }

    static DoipMessage readFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 8);
        int version = header[0] & 0xFF;
        int inverse = header[1] & 0xFF;
        if (version != (PROTOCOL_VERSION & 0xFF) || inverse != (PROTOCOL_VERSION_INVERSE & 0xFF)) {
            throw new IOException("Invalid DoIP version header");
        }
        int payloadType = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        int length = ByteBuffer.wrap(header, 4, 4).getInt();
        if (length < 0 || length > 65536) {
            throw new IOException("Invalid DoIP payload length: " + length);
        }
        byte[] payload = readFully(in, length);
        return new DoipMessage(payloadType, payload);
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new IOException("EOF reading DoIP frame");
            }
            offset += n;
        }
        return buf;
    }

    /** One decoded DoIP message (payload type + payload bytes). */
    static final class DoipMessage {
        private final int payloadType;
        private final byte[] payload;

        DoipMessage(int payloadType, byte[] payload) {
            this.payloadType = payloadType;
            this.payload = payload == null ? new byte[0] : payload.clone();
        }

        int payloadType() {
            return payloadType;
        }

        byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DoipMessage other)) {
                return false;
            }
            return payloadType == other.payloadType && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            return 31 * payloadType + Arrays.hashCode(payload);
        }
    }
}
