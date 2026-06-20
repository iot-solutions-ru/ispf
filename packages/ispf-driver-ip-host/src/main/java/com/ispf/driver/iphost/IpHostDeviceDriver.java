package com.ispf.driver.iphost;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP host driver — unified IT monitoring (PING, HTTP, TCP, DNS, SMTP, FTP).
 */
public class IpHostDeviceDriver implements DeviceDriver {

    private static final DataSchema CHECK_SCHEMA = DataSchema.builder("ipHostCheck")
            .field("reachable", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("latencyMs", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ip-host",
            "IP Host Monitor Driver",
            "0.1.0",
            "Unified IT monitoring: PING, HTTP HEAD, TCP, DNS, SMTP, FTP checks",
            "ISPF",
            Map.of(
                    "defaultHost", "127.0.0.1",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String defaultHost = "127.0.0.1";
    private int timeoutMs = 5000;
    private final Map<String, IpHostPoint> points = new ConcurrentHashMap<>();
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
            case "defaultHost" -> defaultHost = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "IP host driver ready (defaultHost=" + defaultHost + ")");
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
            IpHostPoint point = IpHostPoint.parse(entry.getValue(), defaultHost);
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), check(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IP host driver is read-only in v0.1");
    }

    private DataRecord check(IpHostPoint point) throws DriverException {
        try {
            long start = System.currentTimeMillis();
            boolean reachable;
            String value;
            switch (point.mode()) {
                case PING -> {
                    reachable = InetAddress.getByName(point.target()).isReachable(timeoutMs);
                    value = reachable ? "reachable" : "unreachable";
                }
                case HTTP -> {
                    String url = point.target().startsWith("http")
                            ? point.target()
                            : "http://" + point.target() + (point.port() != 80 ? ":" + point.port() : "");
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setRequestMethod("HEAD");
                    conn.setConnectTimeout(timeoutMs);
                    conn.setReadTimeout(timeoutMs);
                    int code = conn.getResponseCode();
                    reachable = code >= 200 && code < 400;
                    value = String.valueOf(code);
                    conn.disconnect();
                }
                case TCP, SMTP, FTP -> {
                    reachable = tcpConnect(point.target(), point.port());
                    value = reachable ? "open" : "closed";
                }
                case DNS -> {
                    InetAddress addr = InetAddress.getByName(point.target());
                    reachable = addr != null;
                    value = addr == null ? "" : addr.getHostAddress();
                }
                default -> throw new DriverException("Unsupported mode: " + point.mode());
            }
            long latency = System.currentTimeMillis() - start;
            return DataRecord.single(CHECK_SCHEMA, Map.of(
                    "reachable", reachable,
                    "value", value,
                    "latencyMs", (int) latency
            ));
        } catch (Exception e) {
            throw new DriverException("IP host check failed for " + point.mode() + ":" + point.target(), e);
        }
    }

    private boolean tcpConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
