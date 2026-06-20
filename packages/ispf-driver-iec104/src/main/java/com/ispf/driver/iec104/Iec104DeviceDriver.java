package com.ispf.driver.iec104;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.ClientConnectionBuilder;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.ie.IeDoublePointWithQuality;
import org.openmuc.j60870.ie.IeShortFloat;
import org.openmuc.j60870.ie.IeSinglePointWithQuality;
import org.openmuc.j60870.ie.IeNormalizedValue;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IEC 60870-5-104 client driver — polls information objects via read commands.
 * <p>
 * Point mapping: {@code ioa:dataType} e.g. {@code 2001:FLOAT}.
 */
public class Iec104DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec104",
            "IEC 60870-5-104 Driver",
            "0.1.0",
            "Polls IEC 60870-5-104 outstations and maps information object values to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2404",
                    "commonAddress", "1",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("iec104Value")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .build();

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("iec104Bool")
            .field("value", FieldType.BOOLEAN)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private Connection connection;
    private String host = "127.0.0.1";
    private int port = 2404;
    private int commonAddress = 1;
    private int timeoutMs = 5000;
    private final Map<String, Iec104Point> points = new ConcurrentHashMap<>();
    private final Map<Integer, PendingRead> pendingReads = new ConcurrentHashMap<>();
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
        readConfig("commonAddress", value -> commonAddress = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            ClientConnectionBuilder builder = new ClientConnectionBuilder(InetAddress.getByName(host))
                    .setPort(port)
                    .setConnectionTimeout(timeoutMs)
                    .setConnectionEventListener(eventListener);
            connection = builder.build();
            connection.startDataTransfer();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to IEC104 " + host + ":" + port);
        } catch (Exception e) {
            connected = false;
            connection = null;
            throw new DriverException("IEC104 connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        pendingReads.clear();
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // best effort
            }
            connection = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec104Point point = Iec104Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IEC104 write not implemented");
    }

    private DataRecord readPoint(Iec104Point point) throws DriverException {
        PendingRead pending = new PendingRead(point.dataType());
        pendingReads.put(point.ioa(), pending);
        try {
            connection.readCommand(commonAddress, point.ioa());
            if (!pending.await(timeoutMs)) {
                throw new DriverException("IEC104 read timeout for IOA " + point.ioa());
            }
            DataRecord record = pending.result();
            if (record == null) {
                throw new DriverException("IEC104 read returned no value for IOA " + point.ioa());
            }
            return record;
        } catch (IOException e) {
            connected = false;
            throw new DriverException("IEC104 read failed for IOA " + point.ioa(), e);
        } finally {
            pendingReads.remove(point.ioa());
        }
    }

    private void handleAsdu(ASdu aSdu) {
        for (InformationObject informationObject : aSdu.getInformationObjects()) {
            int ioa = informationObject.getInformationObjectAddress();
            PendingRead pending = pendingReads.get(ioa);
            if (pending == null) {
                continue;
            }
            try {
                pending.complete(decodeInformationObject(informationObject, pending.dataType()));
            } catch (DriverException e) {
                pending.fail(e);
            }
        }
    }

    private DataRecord decodeInformationObject(InformationObject informationObject, Iec104Point.Iec104DataType hint)
            throws DriverException {
        InformationElement[][] elements = informationObject.getInformationElements();
        if (elements.length == 0 || elements[0].length == 0) {
            throw new DriverException("No information elements in ASDU");
        }
        InformationElement element = elements[0][0];
        if (element instanceof IeSinglePointWithQuality singlePoint) {
            return DataRecord.single(BOOL_SCHEMA, Map.of(
                    "value", singlePoint.isOn(),
                    "quality", singlePointQuality(singlePoint)
            ));
        }
        if (element instanceof IeDoublePointWithQuality doublePoint) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) doublePoint.getDoublePointInformation().ordinal(),
                    "quality", doublePointQuality(doublePoint)
            ));
        }
        if (element instanceof IeShortFloat shortFloat) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", (double) shortFloat.getValue(),
                    "quality", "GOOD"
            ));
        }
        if (element instanceof IeNormalizedValue normalizedValue) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", normalizedValue.getNormalizedValue(),
                    "quality", "GOOD"
            ));
        }
        return switch (hint) {
            case BOOL, M_SP_NA_1 -> DataRecord.single(BOOL_SCHEMA, Map.of("value", false, "quality", "UNKNOWN"));
            case INT -> DataRecord.single(VALUE_SCHEMA, Map.of("value", 0.0, "quality", "UNKNOWN"));
            default -> DataRecord.single(VALUE_SCHEMA, Map.of("value", 0.0, "quality", "UNKNOWN"));
        };
    }

    private static String singlePointQuality(IeSinglePointWithQuality quality) {
        return quality.isBlocked() ? "BLOCKED"
                : quality.isInvalid() ? "INVALID"
                : quality.isNotTopical() ? "NOT_TOPICAL"
                : quality.isSubstituted() ? "SUBSTITUTED"
                : "GOOD";
    }

    private static String doublePointQuality(IeDoublePointWithQuality quality) {
        return quality.isBlocked() ? "BLOCKED"
                : quality.isInvalid() ? "INVALID"
                : quality.isNotTopical() ? "NOT_TOPICAL"
                : quality.isSubstituted() ? "SUBSTITUTED"
                : "GOOD";
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

    private final ConnectionEventListener eventListener = new ConnectionEventListener() {
        @Override
        public void newASdu(Connection conn, ASdu aSdu) {
            handleAsdu(aSdu);
        }

        @Override
        public void connectionClosed(Connection conn, IOException cause) {
            connected = false;
            pendingReads.values().forEach(PendingRead::cancel);
            pendingReads.clear();
            driverObject.log(DriverLogLevel.WARNING, "IEC104 connection closed");
        }

        @Override
        public void dataTransferStateChanged(Connection conn, boolean active) {
            // no-op
        }
    };

    private static final class PendingRead {
        private final Iec104Point.Iec104DataType dataType;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<DataRecord> result = new AtomicReference<>();
        private final AtomicReference<DriverException> error = new AtomicReference<>();

        PendingRead(Iec104Point.Iec104DataType dataType) {
            this.dataType = dataType;
        }

        Iec104Point.Iec104DataType dataType() {
            return dataType;
        }

        void complete(DataRecord record) {
            result.set(record);
            latch.countDown();
        }

        void fail(DriverException exception) {
            error.set(exception);
            latch.countDown();
        }

        void cancel() {
            latch.countDown();
        }

        boolean await(int timeoutMs) throws DriverException {
            try {
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DriverException("IEC104 read interrupted", e);
            }
            DriverException failure = error.get();
            if (failure != null) {
                throw failure;
            }
            return true;
        }

        DataRecord result() {
            return result.get();
        }
    }
}
