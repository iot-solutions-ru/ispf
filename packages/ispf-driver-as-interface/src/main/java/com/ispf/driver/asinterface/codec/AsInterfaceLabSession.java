package com.ispf.driver.asinterface.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * TCP session for the AS-Interface master/gateway ASCII lab (not AS-i yellow cable PHY).
 * <p>
 * Line dialect:
 * <pre>
 *   GET slave:3:di0{@code \\n}     →  VALUE 1{@code \\n}
 *   SET slave:3:do1 1{@code \\n}  →  OK{@code \\n}
 *   RD slave:3{@code \\n} / WR slave:3 5{@code \\n} aliases accepted by the lab
 * </pre>
 */
public final class AsInterfaceLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public AsInterfaceLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(String wireToken) throws IOException {
        String response = transact("GET " + wireToken);
        return parseValueResponse(response);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        long discrete = Math.round(value);
        String response = transact("SET " + wireToken + " " + discrete);
        if (!response.trim().toUpperCase(Locale.ROOT).startsWith("OK")) {
            throw new IOException("AS-Interface lab SET rejected: " + response);
        }
    }

    private String transact(String command) throws IOException {
        writeLine(command);
        String line = readLine();
        if (line == null) {
            throw new EOFException("EOF from AS-Interface gateway lab");
        }
        return line;
    }

    private void writeLine(String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                buf.write(ch);
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    static double parseValueResponse(String response) throws IOException {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Empty AS-Interface lab response");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IOException("AS-Interface lab GET rejected: " + response);
        }
        if (upper.startsWith("VALUE")) {
            trimmed = trimmed.substring(5).trim();
        } else if (upper.startsWith("OK") && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new IOException("AS-Interface lab non-numeric value: " + response, e);
        }
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
