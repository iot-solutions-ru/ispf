package com.ispf.driver.opchda.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCP session for the OPC HDA HTTP/JSON gateway lab (newline JSON — not Classic HDA / DCOM).
 * <p>
 * Dialect:
 * <pre>
 *   {"op":"get","kind":"item","name":"Tag1"}{@code \\n}
 *   {"op":"get","kind":"tag","name":"Temperature"}{@code \\n}
 *   {"op":"set","kind":"item","name":"Tag1","value":22.5}{@code \\n}
 * </pre>
 * Responses: {@code {"ok":true,"value":...}} / {@code {"ok":false,"error":"..."}}.
 */
public final class OpcHdaLabSession implements AutoCloseable {

    private static final Pattern VALUE_FIELD = Pattern.compile(
            "\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OK_FIELD = Pattern.compile(
            "\"ok\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_FIELD = Pattern.compile(
            "\"error\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public OpcHdaLabSession(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public double readValue(String kind, String name) throws IOException {
        String request = "{\"op\":\"get\",\"kind\":\"" + jsonEscape(kind)
                + "\",\"name\":\"" + jsonEscape(name) + "\"}";
        String response = transact(request);
        return parseValue(response);
    }

    public void writeValue(String kind, String name, double value) throws IOException {
        String request = "{\"op\":\"set\",\"kind\":\"" + jsonEscape(kind)
                + "\",\"name\":\"" + jsonEscape(name)
                + "\",\"value\":" + formatNumber(value) + "}";
        String response = transact(request);
        ensureOk(response);
    }

    private String transact(String jsonLine) throws IOException {
        writeLine(jsonLine);
        String line = readLine();
        if (line == null) {
            throw new EOFException("EOF from OPC HDA gateway lab");
        }
        return line;
    }

    private void writeLine(String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
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
        return buf.toString(StandardCharsets.UTF_8);
    }

    static double parseValue(String response) throws IOException {
        ensureOk(response);
        Matcher matcher = VALUE_FIELD.matcher(response);
        if (!matcher.find()) {
            throw new IOException("OPC HDA gateway lab response missing value: " + response);
        }
        return Double.parseDouble(matcher.group(1));
    }

    static void ensureOk(String response) throws IOException {
        Matcher ok = OK_FIELD.matcher(response);
        if (ok.find() && "false".equalsIgnoreCase(ok.group(1))) {
            Matcher err = ERROR_FIELD.matcher(response);
            String message = err.find() ? err.group(1) : response;
            throw new IOException("OPC HDA gateway lab rejected: " + message);
        }
        if (response.toLowerCase(Locale.ROOT).contains("\"error\"")) {
            Matcher err = ERROR_FIELD.matcher(response);
            if (err.find()) {
                throw new IOException("OPC HDA gateway lab rejected: " + err.group(1));
            }
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static String jsonEscape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
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
