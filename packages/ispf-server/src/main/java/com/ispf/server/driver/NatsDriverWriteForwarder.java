package com.ispf.server.driver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.cluster.NatsEventBridge;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forwards a driver write to the lease owner with NATS request/reply.
 * The owner runs {@link DriverRuntimeService#writePoint} locally and replies with status.
 */
@Component
public class NatsDriverWriteForwarder implements DriverWriteForwarder {

    static final String SUBJECT_PREFIX = "ispf.driver.write.";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Logger log = LoggerFactory.getLogger(NatsDriverWriteForwarder.class);

    private final NatsEventBridge bridge;
    private final NatsProperties natsProperties;
    private final ObjectProvider<DriverRuntimeService> runtime;
    private final ObjectMapper objectMapper;

    public NatsDriverWriteForwarder(
            NatsEventBridge bridge,
            NatsProperties natsProperties,
            ObjectProvider<DriverRuntimeService> runtime,
            ObjectMapper objectMapper
    ) {
        this.bridge = bridge;
        this.natsProperties = natsProperties;
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void subscribe() {
        Connection connection = bridge.connection();
        if (connection == null) {
            return;
        }
        try {
            Dispatcher dispatcher = connection.createDispatcher(this::onMessage);
            dispatcher.subscribe(SUBJECT_PREFIX + subjectToken(natsProperties.replicaId()));
        } catch (RuntimeException ex) {
            log.warn("driver write subscription failed: {}", ex.getMessage());
        }
    }

    @Override
    public boolean available() {
        Connection connection = bridge.connection();
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public DriverRuntimeService.DriverRuntimeStatus forward(
            String ownerReplicaId,
            String devicePath,
            String pointId,
            DataRecord value
    ) {
        Connection connection = bridge.connection();
        if (connection == null) {
            throw new IllegalStateException("NATS is not connected");
        }
        String subject = SUBJECT_PREFIX + subjectToken(ownerReplicaId);
        try {
            Message reply = connection.request(subject, encodeRequest(devicePath, pointId, value), REQUEST_TIMEOUT);
            if (reply == null) {
                throw new IllegalStateException("driver write forward timed out for " + devicePath);
            }
            return decodeStatus(reply.getData());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("driver write forward interrupted for " + devicePath);
        } catch (Exception ex) {
            throw new IllegalStateException("driver write forward failed for " + devicePath + ": " + ex.getMessage());
        }
    }

    void onMessage(Message message) {
        byte[] reply;
        try {
            ParsedRequest request = decodeRequest(message.getData());
            DriverRuntimeService.DriverRuntimeStatus status = runtime.getObject().writePointLocal(
                    request.devicePath(),
                    request.pointId(),
                    request.value()
            );
            reply = objectMapper.writeValueAsBytes(statusBody(status));
        } catch (RuntimeException ex) {
            reply = errorBody(ex.getMessage());
        }
        String replyTo = message.getReplyTo();
        Connection connection = bridge.connection();
        if (replyTo != null && connection != null) {
            connection.publish(replyTo, reply);
        }
    }

    byte[] encodeRequest(String devicePath, String pointId, DataRecord value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devicePath", devicePath);
        body.put("pointId", pointId);
        List<Map<String, String>> fields = new ArrayList<>();
        if (value != null && value.schema() != null) {
            for (FieldDefinition field : value.schema().fields()) {
                fields.add(Map.of("name", field.name(), "type", field.type().name()));
            }
        }
        if (value == null || value.firstRow() == null) {
            throw new IllegalStateException("driver write value is empty");
        }
        for (FieldDefinition field : value.schema().fields()) {
            if (field.type() != FieldType.BOOLEAN
                    && field.type() != FieldType.INTEGER
                    && field.type() != FieldType.LONG
                    && field.type() != FieldType.DOUBLE
                    && field.type() != FieldType.STRING
                    && field.type() != FieldType.DATETIME) {
                throw new IllegalStateException("driver write field type is not forwarded: " + field.type());
            }
        }
        body.put("fields", fields);
        body.put("row", value.firstRow());
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("driver write payload failed: " + ex.getMessage());
        }
    }

    ParsedRequest decodeRequest(byte[] payload) {
        JsonNode root = objectMapper.readTree(payload);
        String devicePath = text(root, "devicePath");
        String pointId = text(root, "pointId");
        DataSchema.Builder schema = DataSchema.builder("write");
        Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
        JsonNode fields = root.get("fields");
        if (fields != null && fields.isArray()) {
            for (int i = 0; i < fields.size(); i++) {
                JsonNode field = fields.get(i);
                FieldType type = FieldType.valueOf(text(field, "type"));
                String name = text(field, "name");
                schema.field(name, type);
                fieldTypes.put(name, type);
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        JsonNode rowNode = root.get("row");
        if (rowNode != null && rowNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : rowNode.properties()) {
                row.put(entry.getKey(), coerce(
                        fieldTypes.getOrDefault(entry.getKey(), FieldType.STRING),
                        entry.getValue()
                ));
            }
        }
        return new ParsedRequest(devicePath, pointId, DataRecord.single(schema.build(), row));
    }

    private DriverRuntimeService.DriverRuntimeStatus decodeStatus(byte[] payload) {
        JsonNode root = objectMapper.readTree(payload);
        if (root.hasNonNull("error")) {
            throw new IllegalStateException(root.get("error").asText());
        }
        return new DriverRuntimeService.DriverRuntimeStatus(
                text(root, "devicePath"),
                text(root, "driverId"),
                text(root, "status"),
                root.path("connected").asBoolean(false),
                root.path("pollIntervalMs").asInt(0),
                root.path("lastError").isNull() ? null : root.path("lastError").asText(null),
                text(root, "telemetryPublishMode"),
                root.path("telemetryCoalesceMs").asInt(0)
        );
    }

    private Map<String, Object> statusBody(DriverRuntimeService.DriverRuntimeStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devicePath", status.devicePath());
        body.put("driverId", status.driverId());
        body.put("status", status.status());
        body.put("connected", status.connected());
        body.put("pollIntervalMs", status.pollIntervalMs());
        body.put("lastError", status.lastError());
        body.put("telemetryPublishMode", status.telemetryPublishMode());
        body.put("telemetryCoalesceMs", status.telemetryCoalesceMs());
        return body;
    }

    private byte[] errorBody(String message) {
        try {
            return objectMapper.writeValueAsBytes(Map.of("error", message == null ? "driver write failed" : message));
        } catch (RuntimeException ex) {
            return "{\"error\":\"driver write failed\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String subjectToken(String ownerReplicaId) {
        if (ownerReplicaId == null || !ownerReplicaId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalStateException("driver write owner is not a NATS subject token: " + ownerReplicaId);
        }
        return ownerReplicaId;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static Object coerce(FieldType type, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return switch (type) {
            case BOOLEAN -> node.asBoolean();
            case INTEGER -> node.asInt();
            case LONG -> node.asLong();
            case DOUBLE -> node.asDouble();
            case STRING, DATETIME -> node.asText();
            default -> throw new IllegalStateException("driver write field type is not forwarded: " + type);
        };
    }

    record ParsedRequest(String devicePath, String pointId, DataRecord value) {
    }
}
