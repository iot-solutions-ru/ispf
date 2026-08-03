package com.ispf.driver.iec104server;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ingress.DriverIngress;
import com.ispf.driver.ingress.DriverIngressBuffer;
import com.ispf.driver.ingress.DriverIngressFifoExecutor;
import com.ispf.driver.ingress.IngressElasticSettings;
import com.ispf.driver.iec104.codec.Iec104Asdu;
import com.ispf.driver.iec104.codec.Iec104Cause;
import com.ispf.driver.iec104.codec.Iec104Connection;
import com.ispf.driver.iec104.codec.Iec104ConnectionListener;
import com.ispf.driver.iec104.codec.Iec104Server;
import com.ispf.driver.iec104.codec.Iec104ServerListener;
import com.ispf.driver.iec104.codec.Iec104Type;
import com.ispf.driver.iec104.codec.Iec104Value;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IEC 60870-5-104 server/slave driver — exposes IOA state written by connected clients.
 * <p>
 * Point mapping: {@code ioa} (information object address).
 */
public class Iec104ServerDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec104-server",
            "IEC 60870-5-104 Server Driver",
            "0.1.0",
            "Hosts an IEC 60870-5-104 slave and exposes last client-written IOA values and connection state",
            "ISPF",
            Map.of(
                    "listenPort", "2404",
                    "commonAddress", "1",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("iec104ServerValue")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .field("clientConnected", FieldType.BOOLEAN)
            .field("clientOriginatorAddress", FieldType.INTEGER)
            .build();

    private static final IngressElasticSettings DEFAULT_ELASTIC = IngressElasticSettings.fixed(2);

    private DriverObject driverObject;
    private Iec104Server server;
    private int listenPort = 2404;
    private int commonAddress = 1;
    private final Map<String, Iec104ServerPoint> points = new ConcurrentHashMap<>();
    private final Map<Integer, Double> ioaValues = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> ioaTypes = new ConcurrentHashMap<>();
    private final AtomicReference<Iec104Connection> activeConnection = new AtomicReference<>();
    private final AtomicReference<String> lastClientInfo = new AtomicReference<>("");
    private final AtomicInteger clientOriginatorAddress = new AtomicInteger(-1);
    private volatile boolean clientConnected;
    private volatile boolean listening;
    private DriverIngressBuffer<String, Iec104ServerPoint> ingressBuffer;
    private DriverIngressFifoExecutor ingressFifo;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("listenPort", value -> listenPort = Integer.parseInt(value));
        readConfig("commonAddress", value -> commonAddress = Integer.parseInt(value));
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "listenPort" -> listenPort = Integer.parseInt(value.trim());
            case "commonAddress" -> commonAddress = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        releaseResources();
        try {
            server = new Iec104Server(listenPort);
            server.start(serverEventListener);
            listening = true;
            startIngress();
            driverObject.log(DriverLogLevel.INFO, "IEC104 server listening on port " + listenPort);
        } catch (IOException e) {
            releaseResources();
            throw new DriverException("IEC104 server start failed", e);
        }
    }

    @Override
    public void disconnect() {
        releaseResources();
    }

    private void releaseResources() {
        listening = false;
        clientConnected = false;
        activeConnection.set(null);
        shutdownIngress();
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private void startIngress() {
        Map<String, String> configuration = driverObject.configuration();
        int queueCapacity = DriverIngress.resolveCapacity(configuration, 10_000);
        if (DriverIngress.resolveFifoIngress(configuration, true)) {
            ingressFifo = new DriverIngressFifoExecutor(
                    IngressElasticSettings.resolve(configuration, DEFAULT_ELASTIC),
                    queueCapacity,
                    "iec104-server-ingress-fifo",
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            return;
        }
        ingressBuffer = new DriverIngressBuffer<>(
                DriverIngress.resolveThreads(configuration, 2),
                queueCapacity,
                (variableName, point) -> driverObject.updateVariable(variableName, readPoint(point)),
                "iec104-server-ingress"
        );
    }

    @Override
    public boolean isConnected() {
        return listening && server != null && !server.isStopped();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec104ServerPoint point = Iec104ServerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        Iec104ServerPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        ioaValues.put(point.ioa(), extractNumeric(value));
        driverObject.updateVariable(pointId, readPoint(point));
    }

    private DataRecord readPoint(Iec104ServerPoint point) {
        double value = ioaValues.getOrDefault(point.ioa(), 0.0);
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "quality", clientConnected ? "GOOD" : "NOT_CONNECTED",
                "clientConnected", clientConnected,
                "clientOriginatorAddress", clientOriginatorAddress.get()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        Object raw = value.firstRow().get("raw");
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("IEC104 server write requires numeric raw/value field");
    }

    private void handleAsdu(Iec104Connection connection, Iec104Asdu asdu) {
        if (asdu.commonAddress() != commonAddress) {
            return;
        }
        if (asdu.typeId() == Iec104Type.C_RD_NA_1) {
            for (Iec104Value value : asdu.values()) {
                sendCurrentValue(connection, value.ioa());
            }
            return;
        }
        if (asdu.typeId() == Iec104Type.C_IC_NA_1) {
            sendAllCurrentValues(connection);
            return;
        }
        for (Iec104Value value : asdu.values()) {
            int ioa = value.ioa();
            Double parsed = decodeWriteValue(value);
            if (parsed != null) {
                ioaValues.put(ioa, parsed);
                ioaTypes.put(ioa, measurementTypeFor(value.typeId()));
                refreshPointsForIoa(ioa);
            }
        }
    }

    private void refreshPointsForIoa(int ioa) {
        DriverIngressBuffer<String, Iec104ServerPoint> buffer = ingressBuffer;
        DriverIngressFifoExecutor fifo = ingressFifo;
        for (Map.Entry<String, Iec104ServerPoint> entry : points.entrySet()) {
            if (entry.getValue().ioa() == ioa) {
                String variableName = entry.getKey();
                Iec104ServerPoint point = entry.getValue();
                if (fifo != null) {
                    fifo.execute(() -> driverObject.updateVariable(variableName, readPoint(point)));
                } else if (buffer != null) {
                    buffer.submit(variableName, point);
                } else {
                    driverObject.updateVariable(variableName, readPoint(point));
                }
            }
        }
    }

    private void sendAllCurrentValues(Iec104Connection connection) {
        for (Iec104ServerPoint point : points.values()) {
            sendCurrentValue(connection, point.ioa());
        }
    }

    private void sendCurrentValue(Iec104Connection connection, int ioa) {
        if (connection == null || !connection.isOpen()) {
            return;
        }
        double current = ioaValues.getOrDefault(ioa, 0.0);
        int type = ioaTypes.getOrDefault(ioa, Iec104Type.M_ME_NC_1);
        Iec104Value value = switch (type) {
            case Iec104Type.M_SP_NA_1 -> Iec104Value.singlePoint(ioa, current != 0.0, "GOOD");
            case Iec104Type.M_ME_NA_1 -> Iec104Value.normalized(ioa, current, "GOOD");
            default -> Iec104Value.shortFloat(ioa, current, "GOOD");
        };
        try {
            connection.sendAsdu(new Iec104Asdu(value.typeId(), Iec104Cause.REQUEST, 0, commonAddress, List.of(value)));
        } catch (IOException e) {
            driverObject.log(DriverLogLevel.DEBUG, "IEC104 read response failed: " + e.getMessage());
        }
    }

    private void shutdownIngress() {
        shutdownIngressFifo();
        shutdownIngressBuffer();
    }

    private void shutdownIngressFifo() {
        DriverIngressFifoExecutor fifo = ingressFifo;
        ingressFifo = null;
        if (fifo != null) {
            fifo.close();
        }
    }

    private void shutdownIngressBuffer() {
        DriverIngressBuffer<String, Iec104ServerPoint> buffer = ingressBuffer;
        ingressBuffer = null;
        if (buffer != null) {
            buffer.shutdown();
        }
    }

    private static Double decodeWriteValue(Iec104Value value) {
        return switch (value.typeId()) {
            case Iec104Type.C_SC_NA_1, Iec104Type.M_SP_NA_1 -> value.booleanValue() ? 1.0 : 0.0;
            case Iec104Type.C_SE_NA_1, Iec104Type.M_ME_NA_1,
                    Iec104Type.C_SE_NC_1, Iec104Type.M_ME_NC_1, Iec104Type.M_ME_TF_1 -> value.numericValue();
            default -> null;
        };
    }

    private static int measurementTypeFor(int typeId) {
        return switch (typeId) {
            case Iec104Type.C_SC_NA_1 -> Iec104Type.M_SP_NA_1;
            case Iec104Type.C_SE_NA_1 -> Iec104Type.M_ME_NA_1;
            case Iec104Type.C_SE_NC_1 -> Iec104Type.M_ME_NC_1;
            default -> typeId;
        };
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

    private final Iec104ServerListener serverEventListener = new Iec104ServerListener() {
        @Override
        public Iec104ConnectionListener onConnection(Iec104Connection connection) {
            activeConnection.set(connection);
            clientConnected = true;
            clientOriginatorAddress.set(connection.originatorAddress());
            lastClientInfo.set(connection.remoteAddress());
            driverObject.log(DriverLogLevel.INFO, "IEC104 client connected from " + lastClientInfo.get());
            return connectionEventListener;
        }

        @Override
        public void onStopped(IOException e) {
            listening = false;
            driverObject.log(DriverLogLevel.WARNING, "IEC104 server stopped listening");
        }

        @Override
        public void onConnectionAttemptFailed(IOException e) {
            driverObject.log(DriverLogLevel.DEBUG, "IEC104 connection attempt failed: " + e.getMessage());
        }
    };

    private final Iec104ConnectionListener connectionEventListener = new Iec104ConnectionListener() {
        @Override
        public void onAsdu(Iec104Connection connection, Iec104Asdu asdu) {
            handleAsdu(connection, asdu);
        }

        @Override
        public void onConnectionClosed(Iec104Connection connection, IOException cause) {
            clientConnected = false;
            clientOriginatorAddress.set(-1);
            activeConnection.compareAndSet(connection, null);
            driverObject.log(DriverLogLevel.INFO, "IEC104 client disconnected");
        }

        @Override
        public void onDataTransferStateChanged(Iec104Connection connection, boolean active) {
            clientConnected = active;
        }
    };
}
