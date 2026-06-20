package com.ispf.driver.dnp3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNP3 TCP driver — validates outstation connectivity and documents point polling.
 * <p>
 * Full DNP3 application-layer reads require native bindings (e.g. {@code io.stepfunc:dnp3}).
 * This driver opens a TCP session to the configured outstation and reports connection state.
 * Point mapping: {@code index:dataType} e.g. {@code 0:ANALOG_INPUT}.
 */
public class Dnp3DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dnp3",
            "DNP3 Driver",
            "0.1.0",
            "Opens DNP3 TCP sessions to outstations; full object reads require native DNP3 bindings",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "20000",
                    "localAddress", "0",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("dnp3Value")
            .field("value", FieldType.STRING)
            .field("status", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private Socket socket;
    private String host = "127.0.0.1";
    private int port = 20000;
    private int timeoutMs = 5000;
    private final Map<String, Dnp3Point> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            connected = true;
            driverObject.log(
                    DriverLogLevel.INFO,
                    "DNP3 TCP connected to " + host + ":" + port
                            + " (application-layer reads need io.stepfunc:dnp3 native bindings)"
            );
        } catch (IOException e) {
            connected = false;
            closeSocket();
            throw new DriverException("DNP3 TCP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Dnp3Point point = Dnp3Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("DNP3 write not implemented");
    }

    private DataRecord readPoint(Dnp3Point point) throws DriverException {
        try {
            // Placeholder: verify socket is alive. Full DNP3 Class 0/1/2/3 poll needs native stack.
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            output.flush();
            int available = input.available();
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", "index=" + point.index() + " type=" + point.dataType(),
                    "status", available >= 0 ? "TCP_CONNECTED" : "TCP_CONNECTED"
            ));
        } catch (IOException e) {
            connected = false;
            throw new DriverException("DNP3 read failed for index " + point.index(), e);
        }
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
            socket = null;
        }
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
