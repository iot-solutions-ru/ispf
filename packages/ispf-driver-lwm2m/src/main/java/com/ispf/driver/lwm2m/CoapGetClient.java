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

    /** Locked message id for production wire (CON GET / ACK). */
    static final int WIRE_MESSAGE_ID = 1;

    private CoapGetClient() {
    }

    record Response(int code, String payload, int messageId) {
    }

    /**
     * CON GET for LwM2M Device object Manufacturer {@code /3/0/0}:
     * header {@code 40 01 00 01}, Uri-Path options {@code B1 33 01 30 01 30}.
     */
    static byte[] encodeConGetDevice300() {
        return buildGet(WIRE_MESSAGE_ID, List.of("3", "0", "0"));
    }

    /**
     * ACK 2.05 Content, no token, MID 1, payload marker + ASCII {@code OK}:
     * {@code 60 45 00 01 FF 4F 4B}.
     */
    static byte[] encodeAckContentOkMid1() {
        return new byte[]{
                0x60, 0x45, 0x00, 0x01,
                (byte) 0xFF, 0x4F, 0x4B
        };
    }

    static Response get(String host, int port, String path, int timeoutMs) throws IOException {
        String normalized = normalizePath(path);
        List<String> segments = pathSegments(normalized);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            byte[] request = buildGet(WIRE_MESSAGE_ID, segments);
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
            return parseResponse(data, WIRE_MESSAGE_ID);
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
        int start = 1;
        while (start < trimmed.length()) {
            int slash = trimmed.indexOf('/', start);
            if (slash < 0) {
                String part = trimmed.substring(start);
                if (!part.isEmpty()) {
                    segments.add(part);
                }
                break;
            }
            if (slash > start) {
                segments.add(trimmed.substring(start, slash));
            }
            start = slash + 1;
        }
        return segments;
    }

    /** CON GET with empty token and Uri-Path options for each segment. */
    static byte[] buildGet(int messageId, List<String> segments) {
        List<byte[]> options = new ArrayList<>();
        int lastOption = 0;
        for (String segment : segments) {
            byte[] value = segment.getBytes(StandardCharsets.UTF_8);
            int delta = OPT_URI_PATH - lastOption;
            lastOption = OPT_URI_PATH;
            options.add(encodeOption(delta, value));
        }
        int optionBytes = options.stream().mapToInt(o -> o.length).sum();
        byte[] frame = new byte[4 + optionBytes];
        // Ver(2)=1 | Type(2)=CON | TKL(4)=0
        frame[0] = (byte) ((1 << 6) | (TYPE_CON << 4) | 0);
        frame[1] = (byte) CODE_GET;
        frame[2] = (byte) ((messageId >> 8) & 0xFF);
        frame[3] = (byte) (messageId & 0xFF);
        int offset = 4;
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

    static Response parseResponse(byte[] frame, int expectedMessageId) throws IOException {
        if (frame.length < 4) {
            throw new IOException("Short CoAP response");
        }
        int ver = (frame[0] >> 6) & 0x03;
        int type = (frame[0] >> 4) & 0x03;
        int tkl = frame[0] & 0x0F;
        if (ver != 1) {
            throw new IOException("Unsupported CoAP version " + ver);
        }
        if (tkl != 0) {
            throw new IOException("Expected empty CoAP token, tkl=" + tkl);
        }
        int code = frame[1] & 0xFF;
        int messageId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (messageId != expectedMessageId) {
            throw new IOException("CoAP message id mismatch");
        }
        int offset = 4;
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

    static ParsedRequest parseRequest(byte[] frame) throws IOException {
        if (frame.length < 4) {
            throw new IOException("Short CoAP request");
        }
        int tkl = frame[0] & 0x0F;
        int code = frame[1] & 0xFF;
        int messageId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
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
        String path = segments.isEmpty() ? "/" : "/" + String.join("/", segments);
        return new ParsedRequest(code, messageId, path);
    }

    record ParsedRequest(int code, int messageId, String path) {
    }
}
