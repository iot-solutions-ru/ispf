package com.ispf.driver.gpstracker;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GPS tracker driver — TCP server that accepts incoming device connections.
 */
public class GpsTrackerDeviceDriver implements DeviceDriver {

    private static final DataSchema FEED_SCHEMA = DataSchema.builder("gpsFeed")
            .field("value", FieldType.STRING)
            .field("bytesRead", FieldType.INTEGER)
            .field("connectedClients", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "gps-tracker",
            "GPS Tracker TCP Server Driver",
            "0.1.0",
            "Listens for incoming GPS device TCP connections and exposes last received line/buffer",
            "ISPF",
            Map.of(
                    "listenPort", "5000",
                    "bufferSize", "4096"
            )
    );

    private DriverObject driverObject;
    private int listenPort = 5000;
    private int bufferSize = 4096;
    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private final AtomicReference<String> lastFeed = new AtomicReference<>("");
    private volatile int connectedClients;
    private final Map<String, GpsTrackerPoint> points = new ConcurrentHashMap<>();
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
            case "listenPort" -> listenPort = Integer.parseInt(value.trim());
            case "bufferSize" -> bufferSize = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            serverSocket = new ServerSocket(listenPort);
            acceptExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "gps-tracker-accept");
                t.setDaemon(true);
                return t;
            });
            acceptExecutor.submit(this::acceptLoop);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "GPS tracker listening on port " + listenPort);
        } catch (IOException e) {
            throw new DriverException("GPS tracker listen failed on port " + listenPort, e);
        }
    }

    private void acceptLoop() {
        while (connected && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                connectedClients++;
                acceptExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (connected) {
                    driverObject.log(DriverLogLevel.WARNING, "GPS tracker accept error: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void handleClient(Socket client) {
        try (client;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[bufferSize];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                String chunk = new String(buffer, 0, read);
                String[] lines = chunk.split("\\R");
                for (String line : lines) {
                    if (!line.isBlank()) {
                        lastFeed.set(line.trim());
                    }
                }
                if (lines.length == 0 || chunk.endsWith("\n") || chunk.endsWith("\r")) {
                    lastFeed.set(chunk.trim());
                }
            }
        } catch (IOException e) {
            driverObject.log(DriverLogLevel.DEBUG, "GPS client disconnected: " + e.getMessage());
        } finally {
            connectedClients = Math.max(0, connectedClients - 1);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // best effort
            }
            serverSocket = null;
        }
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
            acceptExecutor = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && serverSocket != null && !serverSocket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            GpsTrackerPoint point = GpsTrackerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readFeed(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("GPS tracker driver is read-only in v0.1");
    }

    private DataRecord readFeed(GpsTrackerPoint point) {
        String value = lastFeed.get();
        return DataRecord.single(FEED_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "bytesRead", value == null ? 0 : value.length(),
                "connectedClients", connectedClients
        ));
    }
}
