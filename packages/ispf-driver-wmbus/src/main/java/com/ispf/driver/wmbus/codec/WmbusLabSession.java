package com.ispf.driver.wmbus.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Wireless M-Bus TCP gateway lab client.
 * <p>
 * Sends {@code POLL &lt;meter:N|id:HEX&gt;\r\n} and expects {@code TELEGRAM &lt;hex&gt;\r\n}.
 */
public final class WmbusLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public WmbusLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public WmbusLabCodec.ParsedTelegram poll(String token) throws IOException {
        writeLine("POLL " + token);
        String line = readLine();
        if (line == null) {
            throw new EOFException("EOF from wM-Bus gateway");
        }
        String trimmed = line.trim();
        if (!trimmed.regionMatches(true, 0, "TELEGRAM ", 0, 9)) {
            throw new IOException("wM-Bus gateway unexpected response: " + trimmed);
        }
        byte[] frame = WmbusLabCodec.decodeHexTelegram(trimmed.substring(9).trim());
        return WmbusLabCodec.parse(frame);
    }

    private void writeLine(String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
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
