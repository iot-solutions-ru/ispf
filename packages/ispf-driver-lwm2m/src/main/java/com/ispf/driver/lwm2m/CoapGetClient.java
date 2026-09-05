package com.ispf.driver.lwm2m;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clean-room CoAP CON GET client subset (RFC 7252) for LwM2M resource paths.
 * Supports Uri-Path options and 2.05 Content ACK payloads only — not a full CoAP stack
 * and not Eclipse Californium (EPL/GPL concerns avoided).
 */
final class CoapGetClient {

    static final int CODE_GET = 1;
    static final int CODE_CONTENT = 69; // 2.05
    static final int TYPE_CON = 0;
    static final int TYPE_ACK = 2;
    static final int OPT_URI_PATH = 11;

    private CoapGetClient() {
    }

    record Response(int code, String payload, int messageId) {
    }

    static Response get(String host, int port, String path, int timeoutMs) throws IOException {
        String normalized = normalizePath(path);
        List<String> segments = pathSegments(normalized);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            int messageId = ThreadLocalRandom.current().nextInt(1, 0xFFFF);
            byte[] token = new byte[]{(byte) ThreadLocalRandom.current().nextInt(256)};
            byte[] request = buildGet(messageId, token, segments);
            socket.send(new DatagramPacket(request, request.length, new InetSocketAddress(address, port)));

            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                throw new IOException("CoAP GET timeout for " + host + ":" + port + " " + normalized);
            }
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
            return parseResponse(data, messageId, token);
        }
    }

    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("coap://") || trimmed.startsWith("coaps://")) {
            int scheme = trimmed.indexOf("://");
            int slash = trimmed.indexOf('/', scheme + 3);
            trimmed = slash >= 0 ? trimmed.substring(slash) : "/";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    static List<String> pathSegments(String path) {
        List<String> segments = new ArrayList<>();
        String trimmed = normalizePath(path);
        if ("/".equals(trimmed)) {
            return segments;
        }
        for (String part : trimmed.substring(1).split("/")) {
            if (!part.isEmpty()) {
                segments.add(part);
            }
        }
        return segments;
    }

    static byte[] buildGet(int messageId, byte[] token, List<String> segments) {
        int tkl = token.length & 0x0F;
        List<byte[]> options = new ArrayList<>();
        int lastOption = 0;
        for (String segment : segments) {
            byte[] value = segment.getBytes(StandardCharsets.UTF_8);
            int delta = OPT_URI_PATH - lastOption;
            lastOption = OPT_URI_PATH;
            options.add(encodeOption(delta, value));
        }
        int optionBytes = options.stream().mapToInt(o -> o.length).sum();
        byte[] frame = new byte[4 + tkl + optionBytes];
        // Ver(2)=1 | Type(2)=CON | TKL(4)
        frame[0] = (byte) ((1 << 6) | (TYPE_CON << 4) | tkl);
        frame[1] = (byte) CODE_GET;
        frame[2] = (byte) ((messageId >> 8) & 0xFF);
        frame[3] = (byte) (messageId & 0xFF);
        System.arraycopy(token, 0, frame, 4, tkl);
        int offset = 4 + tkl;
        for (byte[] option : options) {
            System.arraycopy(option, 0, frame, offset, option.length);
            offset += option.length;
        }
        return frame;
    }

    static byte[] encodeOption(int delta, byte[] value) {
        int length = value.length;
        // Extended delta/length not needed for Uri-Path digits/short names in this subset
        if (delta > 12 || length > 12) {
            throw new IllegalArgumentException("CoAP option delta/length >12 not supported in subset");
        }
        byte[] out = new byte[1 + length];
        out[0] = (byte) ((delta << 4) | length);
        System.arraycopy(value, 0, out, 1, length);
        return out;
    }

    static Response parseResponse(byte[] frame, int expectedMessageId, byte[] expectedToken) throws IOException {
        if (frame.length < 4) {
            throw new IOException("Short CoAP response");
        }
        int ver = (frame[0] >> 6) & 0x03;
        int type = (frame[0] >> 4) & 0x03;
        int tkl = frame[0] & 0x0F;
        if (ver != 1) {
            throw new IOException("Unsupported CoAP version " + ver);
        }
        int code = frame[1] & 0xFF;
        int messageId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (messageId != expectedMessageId) {
            throw new IOException("CoAP message id mismatch");
        }
        if (frame.length < 4 + tkl) {
            throw new IOException("Truncated CoAP token");
        }
        for (int i = 0; i < tkl && i < expectedToken.length; i++) {
            if (frame[4 + i] != expectedToken[i]) {
                throw new IOException("CoAP token mismatch");
            }
        }
        int offset = 4 + tkl;
        // skip options until payload marker
        while (offset < frame.length && (frame[offset] & 0xFF) != 0xFF) {
            int opt = frame[offset] & 0xFF;
            int delta = (opt >> 4) & 0x0F;
            int len = opt & 0x0F;
            offset += 1;
            if (delta == 13) {
                offset += 1;
            } else if (delta == 14) {
                offset += 2;
            }
            if (len == 13) {
                if (offset >= frame.length) {
                    break;
                }
                len = (frame[offset] & 0xFF) + 13;
                offset += 1;
            } else if (len == 14) {
                if (offset + 1 >= frame.length) {
                    break;
                }
                len = (((frame[offset] & 0xFF) << 8) | (frame[offset + 1] & 0xFF)) + 269;
                offset += 2;
            }
            offset += len;
        }
        String payload = "";
        if (offset < frame.length && (frame[offset] & 0xFF) == 0xFF) {
            payload = new String(frame, offset + 1, frame.length - offset - 1, StandardCharsets.UTF_8);
        }
        if (type != TYPE_ACK && type != TYPE_CON) {
            // still accept payload if present
        }
        return new Response(code, payload, messageId);
    }

    /** Builds a 2.05 Content ACK for the fake LwM2M/CoAP server used in tests. */
    static byte[] buildContentAck(int messageId, byte[] token, String payload) {
        int tkl = token.length & 0x0F;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[4 + tkl + 1 + body.length];
        frame[0] = (byte) ((1 << 6) | (TYPE_ACK << 4) | tkl);
        frame[1] = (byte) CODE_CONTENT;
        frame[2] = (byte) ((messageId >> 8) & 0xFF);
        frame[3] = (byte) (messageId & 0xFF);
        System.arraycopy(token, 0, frame, 4, tkl);
        frame[4 + tkl] = (byte) 0xFF;
        System.arraycopy(body, 0, frame, 5 + tkl, body.length);
        return frame;
    }

    static ParsedRequest parseRequest(byte[] frame) throws IOException {
        if (frame.length < 4) {
            throw new IOException("Short CoAP request");
        }
        int tkl = frame[0] & 0x0F;
        int code = frame[1] & 0xFF;
        int messageId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        byte[] token = new byte[tkl];
        System.arraycopy(frame, 4, token, 0, tkl);
        int offset = 4 + tkl;
        int lastOpt = 0;
        List<String> segments = new ArrayList<>();
        while (offset < frame.length && (frame[offset] & 0xFF) != 0xFF) {
            int header = frame[offset++] & 0xFF;
            int delta = (header >> 4) & 0x0F;
            int len = header & 0x0F;
            int optNum = lastOpt + delta;
            lastOpt = optNum;
            byte[] value = new byte[len];
            System.arraycopy(frame, offset, value, 0, len);
            offset += len;
            if (optNum == OPT_URI_PATH) {
                segments.add(new String(value, StandardCharsets.UTF_8));
            }
        }
        String path = "/" + String.join("/", segments);
        if (segments.isEmpty()) {
            path = "/";
        }
        return new ParsedRequest(code, messageId, token, path);
    }

    record ParsedRequest(int code, int messageId, byte[] token, String path) {
    }
}
