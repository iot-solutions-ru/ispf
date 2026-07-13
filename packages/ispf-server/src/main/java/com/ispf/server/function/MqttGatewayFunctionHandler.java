package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.bootstrap.PlatformReferenceBlueprintBootstrap;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.history.TelemetryHistorianFastPath;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes MQTT ingress on a gateway object to child sensor devices.
 * Triggered from model binding: {@code call(@/fn/dispatchTelemetry, @/lastIngress)}
 * or {@link MqttGatewayIngressDispatchService} (parallel per-topic FIFO workers).
 */
@Component
public class MqttGatewayFunctionHandler implements FunctionHandler {

    public static final String FUNCTION_NAME = "dispatchTelemetry";
    public static final String MODEL_NAME = DemoFixtureBootstrap.MQTT_GATEWAY_MODEL;

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema MEASUREMENT_SCHEMA = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema DISPATCH_OUTPUT_SCHEMA = DataSchema.builder("dispatchTelemetryResult")
            .field("ok", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .field("routedPath", FieldType.STRING)
            .build();

    private static final Pattern DEFAULT_TOPIC_INDEX = Pattern.compile("ispf/loadtest/(\\d+)/temperature");

    private final Set<String> gatewayChildHistorianPrimed = ConcurrentHashMap.newKeySet();

    private final ObjectManager objectManager;
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final ObjectChangePublicationService publicationService;
    private final PlatformScriptBridge platformScriptBridge;
    private final TelemetryHistorianFastPath historianFastPath;

    public MqttGatewayFunctionHandler(
            ObjectManager objectManager,
            DeviceTelemetryPolicyService telemetryPolicyService,
            ObjectChangePublicationService publicationService,
            PlatformScriptBridge platformScriptBridge,
            TelemetryHistorianFastPath historianFastPath
    ) {
        this.objectManager = objectManager;
        this.telemetryPolicyService = telemetryPolicyService;
        this.publicationService = publicationService;
        this.platformScriptBridge = platformScriptBridge;
        this.historianFastPath = historianFastPath;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!FUNCTION_NAME.equals(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        return node.functions().containsKey(FUNCTION_NAME)
                || node.getVariable("lastIngress").isPresent();
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject gateway = objectManager.require(objectPath);
        if (gateway.functions().get(FUNCTION_NAME) == null
                && gateway.getVariable("lastIngress").isEmpty()) {
            throw new IllegalArgumentException("Unknown function: " + FUNCTION_NAME);
        }
        String topic = stringField(input, "topic").orElseGet(() -> stringField(gateway, "lastIngress", "topic").orElse(""));
        String raw = stringField(input, "raw").orElseGet(() -> stringField(gateway, "lastIngress", "raw").orElse(""));
        return dispatchIngress(objectPath, topic, raw);
    }

    public DataRecord dispatchIngress(String gatewayPath, String topic, String raw) {
        return dispatchIngress(gatewayPath, topic, raw, false);
    }

    public DataRecord dispatchIngress(String gatewayPath, String topic, String raw, boolean bypassChildCoalesce) {
        if (topic == null || topic.isBlank()) {
            return failure("missing topic");
        }
        if (raw == null || raw.isBlank()) {
            return failure("missing payload");
        }

        PlatformObject gateway = objectManager.require(gatewayPath);
        String sensorParentPath = stringConfig(gateway, "sensorParentPath")
                .orElse(BlueprintCatalogRoots.INSTANCES);
        String sensorNamePrefix = stringConfig(gateway, "sensorNamePrefix")
                .orElse("loadtest-mqtt-sensor-");
        String topicPattern = stringConfig(gateway, "topicIndexPattern")
                .orElse(DEFAULT_TOPIC_INDEX.pattern());

        Optional<TopicRoute> route = extractTopicRoute(topic, topicPattern);
        if (route.isEmpty()) {
            return failure("topic does not match routing pattern: " + topic);
        }

        String instanceModelName = stringConfig(gateway, "instanceModelName")
                .orElse(PlatformReferenceBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL);
        String instanceName = sensorNamePrefix + route.get().index();
        final String childPath;
        try {
            childPath = platformScriptBridge.instantiateModelIfMissing(
                    instanceModelName,
                    sensorParentPath,
                    instanceName
            );
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }

        Optional<Double> parsed = parseNumeric(raw);
        if (parsed.isEmpty()) {
            return failure("payload is not numeric: " + raw);
        }

        String variableName = route.get().variableName();
        ensureGatewayChildHistorianSampleMode(childPath, variableName);
        DataRecord measurement = DataRecord.single(
                measurementSchema(variableName),
                Map.of("value", parsed.get(), "unit", unitForVariable(variableName))
        );
        updateChildTelemetry(childPath, variableName, measurement, bypassChildCoalesce);
        return DataRecord.single(
                DISPATCH_OUTPUT_SCHEMA,
                Map.of("ok", true, "message", "routed", "routedPath", childPath)
        );
    }

    private void updateChildTelemetry(
            String childPath,
            String variableName,
            DataRecord measurement,
            boolean bypassChildCoalesce
    ) {
        ensureChildLoaded(childPath);
        if (bypassChildCoalesce) {
            objectManager.setDriverTelemetryValueDirect(childPath, variableName, measurement);
            if (historianFastPath.tryPublish(childPath, variableName, measurement, null)
                    || historianFastPath.tryPublishGatewayDispatched(
                            childPath, variableName, measurement, null)) {
                return;
            }
            if (!telemetryPolicyService.automationEligible(childPath, variableName)) {
                return;
            }
            publicationService.publishVariableChange(childPath, variableName, null);
            return;
        }
        objectManager.setDriverTelemetryValue(childPath, variableName, measurement);
    }

    private static DataSchema measurementSchema(String variableName) {
        return "temperature".equals(variableName) ? TEMPERATURE_SCHEMA : MEASUREMENT_SCHEMA;
    }

    private static String unitForVariable(String variableName) {
        return switch (variableName) {
            case "temperature" -> "C";
            case "humidity" -> "%";
            default -> "";
        };
    }

    record TopicRoute(String index, String variableName) {}

    private void ensureChildLoaded(String childPath) {
        try {
            objectManager.require(childPath);
        } catch (ObjectNotFoundException ex) {
            objectManager.syncPathFromDatabase(childPath);
            objectManager.require(childPath);
        }
    }

    /** Bench payloads are constant; gateway lazy children need ALL_VALUES (not CHANGES_ONLY from platform model). */
    private void ensureGatewayChildHistorianSampleMode(String childPath, String variableName) {
        String key = childPath + "|" + variableName;
        if (!gatewayChildHistorianPrimed.add(key)) {
            return;
        }
        if (telemetryPolicyService.historySampleMode(childPath, variableName) == HistorySampleMode.ALL_VALUES) {
            return;
        }
        objectManager.updateVariableHistory(
                childPath,
                variableName,
                true,
                null,
                null,
                HistorySampleMode.ALL_VALUES,
                null,
                null
        );
    }

    private static final Pattern JSON_VALUE_FIELD =
            Pattern.compile("\"value\"\\s*:\\s*(-?(?:\\d+)(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

    static Optional<Double> parseNumeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            Matcher jsonValue = JSON_VALUE_FIELD.matcher(trimmed);
            if (jsonValue.find()) {
                try {
                    double value = Double.parseDouble(jsonValue.group(1));
                    return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        try {
            double value = Double.parseDouble(trimmed);
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static Optional<String> extractIndex(String topic, String pattern) {
        return extractTopicRoute(topic, pattern).map(TopicRoute::index);
    }

    static Optional<TopicRoute> extractTopicRoute(String topic, String pattern) {
        if (topic == null || topic.isBlank() || pattern == null || pattern.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(pattern).matcher(topic);
        if (!matcher.matches() || matcher.groupCount() < 1) {
            return Optional.empty();
        }
        String index = Optional.ofNullable(matcher.group(1)).map(String::trim).filter(value -> !value.isBlank()).orElse(null);
        if (index == null) {
            return Optional.empty();
        }
        String variableName = "temperature";
        if (matcher.groupCount() >= 2) {
            variableName = Optional.ofNullable(matcher.group(2))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .orElse("temperature");
        }
        return Optional.of(new TopicRoute(index, variableName));
    }

    private static DataRecord failure(String message) {
        return DataRecord.single(
                DISPATCH_OUTPUT_SCHEMA,
                Map.of("ok", false, "message", message, "routedPath", "")
        );
    }

    private static Optional<String> stringField(DataRecord record, String field) {
        if (record == null || record.rowCount() == 0) {
            return Optional.empty();
        }
        Object value = record.firstRow().get(field);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    private static Optional<String> stringField(PlatformObject object, String variableName, String field) {
        return object.getVariable(variableName)
                .flatMap(Variable::value)
                .flatMap(record -> stringField(record, field));
    }

    private static Optional<String> stringConfig(PlatformObject object, String variableName) {
        return object.getVariable(variableName)
                .flatMap(Variable::value)
                .flatMap(record -> stringField(record, "value"))
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }
}
