package com.ispf.driver.flexible;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * Flexible driver — TCP/UDP request/response with hex or UTF-8 encoding.
 */
public class FlexibleDeviceDriver implements DeviceDriver {

    private static final DataSchema RESPONSE_SCHEMA = DataSchema.builder("flexibleResponse")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("bytesRead", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "flexible",
            "Flexible Protocol Driver",
            "0.1.0",
            "Sends request bytes/strings over TCP or UDP and maps responses to ISPF variables",
            "ISPF",
            Map.of(
                    "protocol", "TCP",
                    "host", "127.0.0.1",
                    "port", "5000",
                    "timeoutMs", "5000",
                    "encoding", "utf8"
            )
    );

    private DriverObject driverObject;
    private String protocol = "TCP";
    private String host = "127.0.0.1";
    private int port = 5000;
    private int timeoutMs = 5000;
    private String encoding = "utf8";
    private final Map<String, FlexiblePoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

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
            case "protocol" -> protocol = value.trim().toUpperCase(Locale.ROOT);
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "encoding" -> encoding = value.trim().toLowerCase(Locale.ROOT);
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "Flexible driver ready (" + protocol + " " + host + ":" + port + ", encoding=" + encoding + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            FlexiblePoint point = FlexiblePoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), exchange(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Flexible driver is read-only in v0.1");
    }

    private DataRecord exchange(FlexiblePoint point) throws DriverException {
        try {
            if ("UDP".equals(protocol)) {
                return exchangeUdp(point);
            }
            return exchangeTcp(point);
        } catch (IOException e) {
            throw new DriverException("Flexible exchange failed for " + point.request(), e);
        }
    }

    private DataRecord exchangeTcp(FlexiblePoint point) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            byte[] request = encodeRequest(point.request());
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
            String raw = readResponse(socket.getInputStream());
            return toRecord(raw, point);
        }
    }

    private DataRecord exchangeUdp(FlexiblePoint point) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            byte[] request = encodeRequest(point.request());
            DatagramPacket send = new DatagramPacket(
                    request, request.length,
                    InetSocketAddress.createUnresolved(host, port));
            socket.send(send);
            byte[] buffer = new byte[4096];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(receive);
            } catch (SocketTimeoutException e) {
                return toRecord("", point);
            }
            String raw = decodeResponse(buffer, receive.getLength());
            return toRecord(raw, point);
        }
    }

    private DataRecord toRecord(String raw, FlexiblePoint point) {
        String value = raw;
        if (point.responseRegex() != null) {
            Matcher matcher = point.responseRegex().matcher(raw);
            if (matcher.find() && matcher.groupCount() >= 1) {
                value = matcher.group(1);
            } else if (matcher.matches() && matcher.groupCount() >= 1) {
                value = matcher.group(1);
            } else {
                value = "";
            }
        }
        return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "raw", raw == null ? "" : raw,
                "bytesRead", raw == null ? 0 : raw.length()
        ));
    }

    private byte[] encodeRequest(String request) {
        if ("hex".equals(encoding)) {
            return hexToBytes(request.replaceAll("\\s+", ""));
        }
        return request.getBytes(StandardCharsets.UTF_8);
    }

    private String decodeResponse(byte[] buffer, int length) {
        if ("hex".equals(encoding)) {
            return bytesToHex(buffer, length);
        }
        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }

    private static String readResponse(InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        int total = 0;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            while (in.available() > 0 && total < buffer.length) {
                int read = in.read(buffer, total, buffer.length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total > 0 && in.available() == 0) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new String(buffer, 0, total, StandardCharsets.UTF_8);
    }

    static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
