package com.ispf.driver.grpc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC-shaped TCP driver ({@code grpc}) — lab HTTP/2 connection preface and one empty
 * SETTINGS frame, then length-prefixed lab payloads on the same socket.
 * <p>
 * This is <strong>not</strong> HPACK and <strong>not</strong> a protobuf RPC stack —
 * no HEADERS/DATA frames for unary calls, no trailers, no grpc-java. JDK sockets,
 * Apache-2.0 clean-room.
 */
public class GrpcJsonDeviceDriver implements DeviceDriver {

    /** HTTP/2 client connection preface + empty SETTINGS (length 0, type 4, flags 0, stream 0). */
    static final byte[] HTTP2_PREFACE_AND_SETTINGS = {
            0x50, 0x52, 0x49, 0x20, 0x2A, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2F, 0x32, 0x2E, 0x30, 0x0D, 0x0A,
            0x0D, 0x0A, 0x53, 0x4D, 0x0D, 0x0A, 0x0D, 0x0A,
            0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    /** Empty SETTINGS with ACK flag (flags 0x01). */
    static final byte[] HTTP2_SETTINGS_ACK = {
            0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00
    };

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("grpcJsonValue")
            .field("value", FieldType.STRING)
            .field("method", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "grpc",
            "gRPC HTTP/2 Lab Driver",
            "0.2.0",
            "lab HTTP/2 preface and empty SETTINGS; not HPACK and not a protobuf RPC",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "50051",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000",
                    "defaultName", "world"
            ),
            DriverMaturity.BETA,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 50051;
    private int timeoutMs = 5000;
    private String defaultName = "world";
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final Map<String, GrpcJsonPoint> points = new ConcurrentHashMap<>();
    private final Map<String, String> lastRequestNames = new ConcurrentHashMap<>();

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "defaultName" -> defaultName = value.trim();
            case "baseUrl" -> applyBaseUrl(value.trim());
            default -> { }
        }
    }

    /** Accept legacy baseUrl=http://host:port for older configs. */
    private void applyBaseUrl(String baseUrl) {
        String trimmed = baseUrl;
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring("http://".length());
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring("https://".length());
        }
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0) {
            host = trimmed.substring(0, colon);
            port = Integer.parseInt(trimmed.substring(colon + 1));
        } else if (!trimmed.isEmpty()) {
            host = trimmed;
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            Socket next = new Socket();
            next.connect(new InetSocketAddress(host, port), timeoutMs);
            next.setTcpNoDelay(true);
            next.setSoTimeout(timeoutMs);
            InputStream nextIn = next.getInputStream();
            OutputStream nextOut = next.getOutputStream();
            nextOut.write(HTTP2_PREFACE_AND_SETTINGS);
            nextOut.flush();
            byte[] ack = readFully(nextIn, HTTP2_SETTINGS_ACK.length);
            if (!Arrays.equals(HTTP2_SETTINGS_ACK, ack)) {
                next.close();
                throw new DriverException("gRPC lab connect failed: SETTINGS ACK mismatch");
            }
            socket = next;
            in = nextIn;
            out = nextOut;
            driverObject.log(DriverLogLevel.INFO,
                    "gRPC lab HTTP/2 preface connected to " + host + ":" + port
                            + " (not HPACK / not protobuf RPC)");
        } catch (DriverException e) {
            throw e;
        } catch (IOException e) {
            disconnect();
            throw new DriverException("gRPC lab connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
        socket = null;
        in = null;
        out = null;
        points.clear();
        lastRequestNames.clear();
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            GrpcJsonPoint point = GrpcJsonPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            String name = lastRequestNames.getOrDefault(entry.getKey(), defaultName);
            driverObject.updateVariable(entry.getKey(), invoke(point, name));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        GrpcJsonPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown gRPC-JSON point: " + pointId);
        }
        String name = extractWriteValue(value);
        lastRequestNames.put(pointId, name);
        driverObject.updateVariable(pointId, invoke(point, name));
    }

    private DataRecord invoke(GrpcJsonPoint point, String name) throws DriverException {
        // Length-prefixed lab request on the same socket (not HPACK / not protobuf RPC).
        String request = point.serviceMethod() + "\n" + name;
        try {
            writeLengthPrefixed(request.getBytes(StandardCharsets.UTF_8));
            byte[] responseBytes = readLengthPrefixed();
            String response = new String(responseBytes, StandardCharsets.UTF_8);
            Object parsed = lookLikeJson(response) ? GrpcJson.parse(response) : response;
            String value = point.hasField()
                    ? GrpcJson.extractField(parsed, point.field())
                    : GrpcJson.stringify(parsed);
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value == null ? "" : value,
                    "method", point.serviceMethod(),
                    "statusCode", 200
            ));
        } catch (Exception e) {
            throw new DriverException("gRPC lab call failed for " + point.serviceMethod(), e);
        }
    }

    private static boolean lookLikeJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private void writeLengthPrefixed(byte[] payload) throws IOException {
        if (payload.length > 0xFFFF) {
            throw new IOException("gRPC lab payload too long");
        }
        out.write((payload.length >> 8) & 0xFF);
        out.write(payload.length & 0xFF);
        out.write(payload);
        out.flush();
    }

    private byte[] readLengthPrefixed() throws IOException {
        byte[] header = readFully(in, 2);
        int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        return readFully(in, length);
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new EOFException("EOF reading gRPC lab bytes, need " + length + " got " + offset);
            }
            offset += n;
        }
        return buf;
    }

    private static String extractWriteValue(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("gRPC-JSON write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "name", "raw")) {
            Object raw = row.get(key);
            if (raw != null) {
                return String.valueOf(raw);
            }
        }
        throw new DriverException("gRPC-JSON write requires value, name, or raw field");
    }
}
