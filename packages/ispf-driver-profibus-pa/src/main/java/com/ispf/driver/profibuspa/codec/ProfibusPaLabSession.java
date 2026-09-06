package com.ispf.driver.profibuspa.codec;

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
 * TCP session for the PROFIBUS PA instrument gateway ASCII lab
 * (not native DP/PA coupler ASIC / RS-485 PA PHY).
 * <p>
 * Line dialect (lab gateway on TCP — default port 9600):
 * <pre>
 *   RD slot:1:pv{@code \\n}       →  VALUE 12.5{@code \\n}
 *   WR addr:12 21.0{@code \\n}    →  OK{@code \\n}
 * </pre>
 */
public final class ProfibusPaLabSession implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public ProfibusPaLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(String wireToken) throws IOException {
        String response = transact("RD " + wireToken);
        return parseValueResponse(response);
    }

    public void writeValue(String wireToken, double value) throws IOException {
        String response = transact("WR " + wireToken + " " + value);
        if (!response.trim().toUpperCase(Locale.ROOT).startsWith("OK")) {
            throw new IOException("PROFIBUS PA lab WR rejected: " + response);
        }
    }

    private String transact(String command) throws IOException {
        writeLine(command);
        String line = readLine();
        if (line == null) {
            throw new EOFException("EOF from PROFIBUS PA-over-TCP gateway lab");
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
            throw new IOException("Empty PROFIBUS PA lab response");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IOException("PROFIBUS PA lab RD rejected: " + response);
        }
        if (upper.startsWith("VALUE")) {
            trimmed = trimmed.substring(5).trim();
        } else if (upper.startsWith("OK") && trimmed.length() > 2) {
            trimmed = trimmed.substring(2).trim();
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new IOException("PROFIBUS PA lab non-numeric value: " + response, e);
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
