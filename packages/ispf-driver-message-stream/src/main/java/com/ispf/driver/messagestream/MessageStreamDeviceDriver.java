package com.ispf.driver.messagestream;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message stream driver — TCP/UDP client or listener for byte streams.
 */
public class MessageStreamDeviceDriver implements DeviceDriver {

    private static final DataSchema STREAM_SCHEMA = DataSchema.builder("messageStream")
            .field("stream", FieldType.STRING)
            .field("bytesRead", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "message-stream",
            "Message Stream Driver",
            "0.1.0",
            "Reads TCP/UDP message streams (client connect or listen mode)",
            "ISPF",
            Map.of(
                    "protocol", "TCP",
                    "host", "127.0.0.1",
                    "port", "5000",
                    "listen", "false",
                    "bufferSize", "4096",
                    "pollIntervalMs", "1000"
            )
    );

    private DriverObject driverObject;
    private String protocol = "TCP";
    private String host = "127.0.0.1";
    private int port = 5000;
    private boolean listen = false;
    private int bufferSize = 4096;
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private final Map<String, String> points = new ConcurrentHashMap<>();
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
            case "listen" -> listen = Boolean.parseBoolean(value.trim());
            case "bufferSize" -> bufferSize = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if ("UDP".equals(protocol)) {
                if (listen) {
                    udpSocket = new DatagramSocket(port);
                } else {
                    udpSocket = new DatagramSocket();
                    udpSocket.connect(new InetSocketAddress(host, port));
                }
                udpSocket.setSoTimeout(1000);
            } else {
                if (listen) {
                    throw new DriverException("TCP listen mode is not supported in v0.1; use UDP listen");
                }
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(host, port), 5000);
                tcpSocket.setSoTimeout(1000);
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "Message stream ready (" + protocol + (listen ? " listen" : " client") + " port=" + port + ")");
        } catch (IOException e) {
            throw new DriverException("Message stream connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeQuietly(tcpSocket);
        tcpSocket = null;
        closeQuietly(udpSocket);
        udpSocket = null;
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
        DataRecord record = pollStream();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            points.put(entry.getKey(), entry.getValue());
            driverObject.updateVariable(entry.getKey(), record);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Message stream driver is read-only in v0.1");
    }

    private DataRecord pollStream() throws DriverException {
        try {
            byte[] buffer = new byte[bufferSize];
            int read;
            if ("UDP".equals(protocol)) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    udpSocket.receive(packet);
                    read = packet.getLength();
                } catch (SocketTimeoutException e) {
                    read = 0;
                }
            } else {
                read = tcpSocket.getInputStream().available();
                if (read > 0) {
                    read = Math.min(read, bufferSize);
                    read = tcpSocket.getInputStream().read(buffer, 0, read);
                } else {
                    read = 0;
                }
            }
            String text = read > 0
                    ? new String(buffer, 0, read, StandardCharsets.UTF_8)
                    : "";
            return DataRecord.single(STREAM_SCHEMA, Map.of(
                    "stream", text,
                    "bytesRead", read
            ));
        } catch (IOException e) {
            throw new DriverException("Message stream read failed", e);
        }
    }

    private static void closeQuietly(java.net.Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static void closeQuietly(DatagramSocket socket) {
        if (socket != null) {
            socket.close();
        }
    }
}
