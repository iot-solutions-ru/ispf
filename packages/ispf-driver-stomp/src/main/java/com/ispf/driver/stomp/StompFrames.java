package com.ispf.driver.stomp;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** STOMP 1.2 frame codec. Heartbeat octets are skipped. A frame ends at the NULL that follows the body. */
final class StompFrames {

    record Frame(String command, Map<String, String> headers, String body) {
    }

    private StompFrames() {
    }

    static byte[] encode(String command, Map<String, String> headers, String body) {
        StringBuilder text = new StringBuilder();
        text.append(command).append('\n');
        for (Map.Entry<String, String> header : headers.entrySet()) {
            text.append(header.getKey()).append(':').append(escape(header.getValue())).append('\n');
        }
        text.append('\n');
        text.append(body == null ? "" : body);
        byte[] head = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[head.length + 1];
        System.arraycopy(head, 0, frame, 0, head.length);
        frame[head.length] = 0;
        return frame;
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> out.append("\\\\");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case ':' -> out.append("\\c");
                default -> out.append(current);
            }
        }
        return out.toString();
    }

    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                out.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> out.append('\\');
                case 'r' -> out.append('\r');
                case 'n' -> out.append('\n');
                case 'c' -> out.append(':');
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    /** Bytes already read from the socket. {@link #poll()} returns null until one whole frame is present. */
    static final class Buffer {
        private byte[] data = new byte[512];
        private int from;
        private int to;

        void append(byte[] chunk) {
            if (chunk == null || chunk.length == 0) {
                return;
            }
            ensure(chunk.length);
            System.arraycopy(chunk, 0, data, to, chunk.length);
            to += chunk.length;
        }

        Frame poll() {
            int begin = skipHeartbeats(from);
            if (begin >= to) {
                from = to = 0;
                return null;
            }
            int bodyStart = headerEnd(begin);
            if (bodyStart < 0) {
                from = begin;
                compact();
                return null;
            }
            String command = null;
            Map<String, String> headers = new LinkedHashMap<>();
            int line = begin;
            while (line < bodyStart) {
                int lineEnd = indexOf(line, (byte) '\n');
                if (lineEnd < 0 || lineEnd >= bodyStart) {
                    break;
                }
                int textEnd = lineEnd > line && data[lineEnd - 1] == '\r' ? lineEnd - 1 : lineEnd;
                if (textEnd > line) {
                    String text = new String(data, line, textEnd - line, StandardCharsets.UTF_8);
                    if (command == null) {
                        command = text.trim();
                    } else {
                        int colon = text.indexOf(':');
                        if (colon > 0) {
                            headers.put(text.substring(0, colon), unescape(text.substring(colon + 1)));
                        }
                    }
                }
                line = lineEnd + 1;
            }
            if (command == null || command.isBlank()) {
                from = begin;
                compact();
                return null;
            }
            Integer length = contentLength(headers.get("content-length"));
            int end;
            if (length != null) {
                if (length < 0 || bodyStart > to - length) {
                    from = begin;
                    compact();
                    return null;
                }
                end = bodyStart + length;
                if (end < to && data[end] == 0) {
                    end++;
                } else if (end >= to) {
                    from = begin;
                    compact();
                    return null;
                }
            } else {
                end = indexOf(bodyStart, (byte) 0);
                if (end < 0) {
                    from = begin;
                    compact();
                    return null;
                }
                end++;
            }
            int bodyEnd = length != null ? bodyStart + length : end - 1;
            String body = new String(data, bodyStart, Math.max(0, bodyEnd - bodyStart), StandardCharsets.UTF_8);
            from = end;
            if (from >= to) {
                from = to = 0;
            } else {
                compact();
            }
            return new Frame(command, headers, body);
        }

        private int skipHeartbeats(int index) {
            while (index < to && (data[index] == '\n' || data[index] == '\r')) {
                index++;
            }
            return index;
        }

        /** Index of the first body byte, or -1 when the blank line is not here yet. */
        private int headerEnd(int begin) {
            int line = begin;
            while (line < to) {
                int lineEnd = indexOf(line, (byte) '\n');
                if (lineEnd < 0) {
                    return -1;
                }
                boolean blank = lineEnd == line || (lineEnd == line + 1 && data[line] == '\r');
                if (blank) {
                    return lineEnd + 1;
                }
                line = lineEnd + 1;
            }
            return -1;
        }

        private int indexOf(int start, byte needle) {
            for (int i = start; i < to; i++) {
                if (data[i] == needle) {
                    return i;
                }
            }
            return -1;
        }

        private static Integer contentLength(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private void ensure(int extra) {
            if (to + extra <= data.length) {
                return;
            }
            compact();
            if (to + extra <= data.length) {
                return;
            }
            byte[] grown = new byte[Math.max(data.length * 2, to + extra)];
            System.arraycopy(data, 0, grown, 0, to);
            data = grown;
        }

        private void compact() {
            if (from <= 0) {
                return;
            }
            int keep = to - from;
            System.arraycopy(data, from, data, 0, keep);
            from = 0;
            to = keep;
        }
    }
}
