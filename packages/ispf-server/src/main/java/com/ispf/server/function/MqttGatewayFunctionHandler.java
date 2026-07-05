package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.bootstrap.FixtureBlueprintBootstrap;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.history.TelemetryHistorianFastPath;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes MQTT ingress on a gateway object to child sensor devices.
 * Triggered from model binding: {@code callFunction(dispatchTelemetry, lastIngress)}
 * or {@link MqttGatewayIngressDispatchService} (parallel per-topic FIFO workers).
 */
@Component
public class MqttGatewayFunctionHandler implements FunctionHandler {

    public static final String FUNCTION_NAME = "dispatchTelemetry";
    public static final String MODEL_NAME = FixtureBlueprintBootstrap.MQTT_GATEWAY_MODEL;

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema DISPATCH_OUTPUT_SCHEMA = DataSchema.builder("dispatchTelemetryResult")
            .field("ok", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .field("routedPath", FieldType.STRING)
            .build();

    private static final Pattern DEFAULT_TOPIC_INDEX = Pattern.compile("ispf/loadtest/(\\d+)/temperature");

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
        return node != null && node.functions().containsKey(FUNCTION_NAME);
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject gateway = objectManager.require(objectPath);
        if (gateway.functions().get(FUNCTION_NAME) == null) {
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

        Optional<String> index = extractIndex(topic, topicPattern);
        if (index.isEmpty()) {
            return failure("topic does not match routing pattern: " + topic);
        }

        String instanceModelName = stringConfig(gateway, "instanceModelName")
                .orElse(FixtureBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL);
        String instanceName = sensorNamePrefix + index.get();
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

        DataRecord temperature = DataRecord.single(
                TEMPERATURE_SCHEMA,
                Map.of("value", parsed.get(), "unit", "C")
        );
        updateChildTelemetry(childPath, temperature, bypassChildCoalesce);
        return DataRecord.single(
                DISPATCH_OUTPUT_SCHEMA,
                Map.of("ok", true, "message", "routed", "routedPath", childPath)
        );
    }

    private void updateChildTelemetry(String childPath, DataRecord temperature, boolean bypassChildCoalesce) {
        boolean automationEligible = telemetryPolicyService.automationEligible(childPath);
        if (bypassChildCoalesce && !automationEligible) {
            objectManager.setDriverTelemetryValueDirect(childPath, "temperature", temperature);
            if (!historianFastPath.tryPublish(childPath, "temperature", temperature, null)) {
                publicationService.publishVariableChange(childPath, "temperature", null);
            }
            return;
        }
        objectManager.setDriverTelemetryValue(childPath, "temperature", temperature);
    }

    static Optional<Double> parseNumeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static Optional<String> extractIndex(String topic, String pattern) {
        if (topic == null || topic.isBlank() || pattern == null || pattern.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(pattern).matcher(topic);
        if (!matcher.matches() || matcher.groupCount() < 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group(1)).map(String::trim).filter(value -> !value.isBlank());
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
