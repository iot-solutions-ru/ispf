package com.ispf.driver.fanucfocas.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * TCP session for the Fanuc FOCAS-shaped CNC gateway lab (default port {@code 8193}).
 * <p>
 * Honesty: length-prefixed ASCII CNC gateway lab — not Fanuc FOCAS library / proprietary SDK / real CNC.
 * <p>
 * Framing: {@code [uint16 BE length][ASCII payload]}.
 * Dialect payloads:
 * <pre>
 *   GET pmc:D0001
 *   SET pmc:D0001 42
 *   GET cnc:abs:1
 *   GET stat:run
 * </pre>
 * Responses: {@code OK &lt;value&gt;} / {@code ERR &lt;message&gt;}.
 */
public final class FanucFocasLabSession implements AutoCloseable {

    private static final int MAX_FRAME = 4096;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public FanucFocasLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(String point) throws IOException {
        String response = transact("GET " + point);
        return parseOkValue(response);
    }

    public void writeValue(String point, double value) throws IOException {
        String response = transact("SET " + point + " " + formatNumber(value));
        parseOkValue(response);
    }

    private String transact(String payload) throws IOException {
        writeFrame(payload);
        String response = readFrame();
        if (response == null) {
            throw new EOFException("EOF from Fanuc FOCAS gateway lab");
        }
        return response;
    }

    private void writeFrame(String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.US_ASCII);
        if (body.length > MAX_FRAME) {
            throw new IOException("Fanuc FOCAS gateway lab frame too large: " + body.length);
        }
        byte[] header = ByteBuffer.allocate(2).putShort((short) body.length).array();
        out.write(header);
        out.write(body);
        out.flush();
    }

    private String readFrame() throws IOException {
        byte[] header = readFully(2);
        int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        if (length < 0 || length > MAX_FRAME) {
            throw new IOException("Fanuc FOCAS gateway lab invalid frame length: " + length);
        }
        if (length == 0) {
            return "";
        }
        byte[] body = readFully(length);
        return new String(body, StandardCharsets.US_ASCII);
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new EOFException("EOF reading Fanuc FOCAS gateway lab frame");
            }
            offset += n;
        }
        return buf;
    }

    static double parseOkValue(String response) throws IOException {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Empty Fanuc FOCAS gateway lab response");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IOException("Fanuc FOCAS gateway lab rejected: " + trimmed);
        }
        if (upper.startsWith("OK")) {
            trimmed = trimmed.substring(2).trim();
        } else if (upper.startsWith("VALUE")) {
            trimmed = trimmed.substring(5).trim();
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new IOException("Fanuc FOCAS gateway lab non-numeric value: " + response, e);
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
