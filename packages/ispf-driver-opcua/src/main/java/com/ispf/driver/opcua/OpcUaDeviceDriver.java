package com.ispf.driver.opcua;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverDiscovery;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverPollTimestamps;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OPC UA client driver — reads node Value attributes via Eclipse Milo.
 * <p>
 * Point mapping: {@code nodeId} e.g. {@code ns=2;s=Temperature} or {@code i=2258}.
 * Config {@code readMode}: {@code poll} (default) or {@code subscribe} (push updates with poll fallback).
 */
public class OpcUaDeviceDriver implements DeviceDriver, DriverDiscovery {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opcua",
            "OPC UA Driver",
            "0.1.0",
            "Polls and writes OPC UA servers (SecurityPolicy None) and maps node values to ISPF variables",
            "ISPF",
            Map.of(
                    "endpointUrl", "opc.tcp://localhost:4840",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("opcUaValue")
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private OpcUaClient client;
    private String endpointUrl = "opc.tcp://localhost:4840";
    private int timeoutMs = 5000;
    private String readMode = "poll";
    private final Map<String, OpcUaPoint> points = new ConcurrentHashMap<>();
    private ManagedSubscription subscription;
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("endpointUrl", value -> endpointUrl = value);
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
        readConfig("readMode", value -> readMode = value.trim());
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "endpointUrl" -> endpointUrl = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value);
            case "readMode" -> readMode = value.trim();
            default -> {
            }
        }
    }

    @Override
    public List<DriverDiscovery.Node> browseChildren(String parentNodeId) throws DriverException {
        if (isConnected()) {
            return browseWithClient(parentNodeId);
        }
        return OpcUaBrowseSupport.browseChildren(endpointUrl, parentNodeId, timeoutMs).stream()
                .map(node -> new DriverDiscovery.Node(node.nodeId(), node.displayName(), node.nodeClass()))
                .toList();
    }

    @Override
    public void connect() throws DriverException {
        try {
            client = OpcUaClient.create(
                    endpointUrl,
                    endpoints -> endpoints.stream()
                            .filter(endpoint -> SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri()))
                            .findFirst(),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("ISPF OPC UA Driver"))
                            .setApplicationUri("urn:ispf:driver:opcua")
                            .build()
            );
            client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to OPC UA " + endpointUrl);
        } catch (Exception e) {
            connected = false;
            client = null;
            throw new DriverException("OPC UA connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        subscription = null;
        if (client != null) {
            try {
                client.disconnect().get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // best effort
            }
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpcUaPoint point = OpcUaPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
        }
        if ("subscribe".equalsIgnoreCase(readMode)) {
            try {
                ensureSubscription(pointMappings);
                return;
            } catch (Exception e) {
                driverObject.log(DriverLogLevel.WARNING, "OPC UA subscribe failed, falling back to poll: " + e.getMessage());
            }
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpcUaPoint point = points.get(entry.getKey());
            PointRead read = readPoint(point);
            driverObject.updateVariable(entry.getKey(), read.record(), DriverPollTimestamps.sourceOrPollTick(read.observedAt()));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OpcUaPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        try {
            DataValue current = client.readValue(
                    0.0,
                    TimestampsToReturn.Neither,
                    point.nodeId()
            ).get(timeoutMs, TimeUnit.MILLISECONDS);
            Variant variant = toWriteVariant(extractWriteObject(value), current.getValue());
            StatusCode status = client.writeValue(point.nodeId(), new DataValue(variant))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (status == null || !status.isGood()) {
                throw new DriverException("OPC UA write rejected: " + status);
            }
            PointRead read = readPoint(point);
            driverObject.updateVariable(pointId, read.record(), DriverPollTimestamps.sourceOrPollTick(read.observedAt()));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OPC UA write failed for point " + pointId, e);
        }
    }

    private record PointRead(DataRecord record, Instant observedAt) {
    }

    private void ensureSubscription(Map<String, String> pointMappings) throws Exception {
        if (subscription == null) {
            subscription = ManagedSubscription.create(client);
            subscription.setDefaultMonitoringMode(MonitoringMode.Reporting);
        } else if (!subscription.getDataItems().isEmpty()) {
            subscription.deleteDataItems(subscription.getDataItems());
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(pointMappings.entrySet());
        List<NodeId> nodeIds = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries) {
            nodeIds.add(OpcUaPoint.parse(entry.getValue()).nodeId());
        }
        List<ManagedDataItem> items = subscription.createDataItems(nodeIds);
        for (int i = 0; i < items.size() && i < entries.size(); i++) {
            ManagedDataItem item = items.get(i);
            String variableName = entries.get(i).getKey();
            item.addDataValueListener((dataItem, dataValue) -> {
                PointRead read = toPointRead(dataValue);
                driverObject.updateVariable(
                        variableName,
                        read.record(),
                        DriverPollTimestamps.sourceOrPollTick(read.observedAt())
                );
            });
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpcUaPoint point = points.get(entry.getKey());
            PointRead read = readPoint(point);
            driverObject.updateVariable(
                    entry.getKey(),
                    read.record(),
                    DriverPollTimestamps.sourceOrPollTick(read.observedAt())
            );
        }
    }

    private List<DriverDiscovery.Node> browseWithClient(String parentNodeId) throws DriverException {
        try {
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId parent =
                    parentNodeId == null || parentNodeId.isBlank()
                            ? org.eclipse.milo.opcua.stack.core.Identifiers.ObjectsFolder
                            : OpcUaPoint.parse(parentNodeId).nodeId();
            List<org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription> refs =
                    client.getAddressSpace().browse(parent);
            List<DriverDiscovery.Node> nodes = new ArrayList<>();
            for (var ref : refs) {
                var nodeId = ref.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
                if (nodeId == null) {
                    continue;
                }
                String displayName = ref.getBrowseName() != null ? ref.getBrowseName().getName() : nodeId.toParseableString();
                nodes.add(new DriverDiscovery.Node(
                        nodeId.toParseableString(),
                        displayName,
                        ref.getNodeClass() != null ? ref.getNodeClass().name() : "Unknown"
                ));
            }
            return nodes;
        } catch (Exception e) {
            throw new DriverException("OPC UA browse failed", e);
        }
    }

    private PointRead toPointRead(DataValue dataValue) {
        Object rawValue = dataValue.getValue() != null ? dataValue.getValue().getValue() : null;
        String quality = dataValue.getStatusCode() != null ? dataValue.getStatusCode().toString() : "UNKNOWN";
        Instant observedAt = null;
        if (dataValue.getSourceTime() != null) {
            observedAt = dataValue.getSourceTime().getJavaInstant();
        } else if (dataValue.getServerTime() != null) {
            observedAt = dataValue.getServerTime().getJavaInstant();
        }
        DataRecord record = DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", rawValue != null ? rawValue.toString() : "",
                "quality", quality
        ));
        return new PointRead(record, observedAt);
    }

    private PointRead readPoint(OpcUaPoint point) throws DriverException {
        try {
            DataValue dataValue = client.readValue(
                    0.0,
                    TimestampsToReturn.Both,
                    point.nodeId()
            ).get(timeoutMs, TimeUnit.MILLISECONDS);
            return toPointRead(dataValue);
        } catch (Exception e) {
            throw new DriverException("OPC UA read failed at " + point.nodeId(), e);
        }
    }

    private static Object extractWriteObject(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("raw");
        if (raw != null) {
            return raw;
        }
        Object val = value.firstRow().get("value");
        if (val != null) {
            return val;
        }
        throw new DriverException("OPC UA write requires value or raw field");
    }

    private static Variant toWriteVariant(Object writeObject, Variant current) {
        Object sample = current != null && !current.isNull() ? current.getValue() : null;
        return new Variant(coerceValue(writeObject, sample));
    }

    private static Object coerceValue(Object writeObject, Object sample) {
        if (sample instanceof Boolean) {
            return toBoolean(writeObject);
        }
        if (sample instanceof Float) {
            return (float) toDouble(writeObject);
        }
        if (sample instanceof Double) {
            return toDouble(writeObject);
        }
        if (sample instanceof Byte) {
            return (byte) toLong(writeObject);
        }
        if (sample instanceof Short) {
            return (short) toLong(writeObject);
        }
        if (sample instanceof Integer) {
            return (int) toLong(writeObject);
        }
        if (sample instanceof Long) {
            return toLong(writeObject);
        }
        if (sample instanceof UByte) {
            return Unsigned.ubyte((short) toLong(writeObject));
        }
        if (sample instanceof UShort) {
            return Unsigned.ushort((int) toLong(writeObject));
        }
        if (sample instanceof UInteger) {
            return Unsigned.uint(toLong(writeObject));
        }
        if (sample instanceof ULong) {
            return Unsigned.ulong(toLong(writeObject));
        }
        if (sample instanceof String) {
            return writeObject.toString();
        }
        if (writeObject instanceof Boolean bool) {
            return bool;
        }
        if (writeObject instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d)) {
                return (long) d;
            }
            return d;
        }
        return writeObject.toString();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
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
