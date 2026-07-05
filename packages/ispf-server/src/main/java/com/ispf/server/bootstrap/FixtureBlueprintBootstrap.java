package com.ispf.server.bootstrap;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional demo / lab Relative Blueprints — registered only when platform fixtures are enabled.
 * Solution-specific models belong in application bundles ({@code models[]} in manifest).
 */
@Component
public class FixtureBlueprintBootstrap {

    public static final String MQTT_SENSOR_MODEL = "mqtt-sensor-v1";
    /** INSTANCE type for MQTT gateway child sensors (instantiate under gateway.sensors). */
    public static final String MQTT_GATEWAY_SENSOR_MODEL = "mqtt-gateway-sensor-v1";
    public static final String MQTT_GATEWAY_MODEL = "mqtt-gateway-v1";
    public static final String MQTT_METER_BUS_MODEL = "mqtt-meter-bus-v1";
    public static final String METERS_MODEL = "Meters";
    public static final String DEVICE_DRIVER_MODEL = "device-driver-v1";
    public static final String BASE_SENSOR_MODEL = "base-sensor-v1";
    public static final String VENDOR_SENSOR_EXT_MODEL = "vendor-sensor-ext-v1";
    public static final String SNMP_AGENT_MODEL = "snmp-agent-v1";
    public static final String SNMP_LOCALHOST_PATH = "root.platform.devices.snmp-localhost";
    public static final String VENDOR_SENSOR_DEMO_PATH = "root.platform.devices.vendor-sensor-demo";
    public static final String MQTT_METER_BUS_PATH = "root.platform.devices.mqtt-meter-bus";
    public static final String INGEST_METER_PAYLOAD_FUNCTION = "ingestMeterPayload";

    public static final String METER_INGRESS_DRIVER_CONFIG =
            "{\"ingressVariable\":\"lastIngress\",\"brokerUrl\":\"tcp://127.0.0.1:1883\","
                    + "\"ingressPayloadLanes\":true,\"telemetryCoalesceMs\":10}";
    public static final String METER_INGRESS_POINT_MAPPINGS = "{\"ingress\":\"meter\"}";

    public static final String METER_INGEST_SCRIPT_BODY =
            "{\"steps\":["
                    + "{\"type\":\"jsonParse\",\"source\":\"${input.raw}\",\"var\":\"meter\",\"fields\":[\"id\",\"temperature\"]},"
                    + "{\"type\":\"failIfNull\",\"var\":\"meter.id\",\"message\":\"missing id\"},"
                    + "{\"type\":\"readVariable\",\"objectPath\":\"self\",\"variable\":\"instanceParentPath\",\"field\":\"value\",\"var\":\"parentPath\"},"
                    + "{\"type\":\"readVariable\",\"objectPath\":\"self\",\"variable\":\"instanceModelName\",\"field\":\"value\",\"var\":\"modelName\"},"
                    + "{\"type\":\"instantiateModelIfMissing\",\"modelName\":\"${modelName}\",\"parentPath\":\"${parentPath}\",\"instanceName\":\"${meter.id}\",\"var\":\"instancePath\"},"
                    + "{\"type\":\"setDriverTelemetry\",\"objectPath\":\"${instancePath}\",\"variable\":\"temperature\",\"fields\":{\"value\":\"${meter.temperature}\",\"unit\":\"C\"}},"
                    + "{\"type\":\"return\",\"fields\":{\"ok\":true,\"message\":\"ingested\",\"routedPath\":\"${instancePath}\"}}"
                    + "]}";

    public static final List<String> FIXTURE_MODEL_NAMES = List.of(
            MQTT_SENSOR_MODEL,
            MQTT_GATEWAY_SENSOR_MODEL,
            MQTT_GATEWAY_MODEL,
            MQTT_METER_BUS_MODEL,
            METERS_MODEL,
            DEVICE_DRIVER_MODEL,
            BASE_SENSOR_MODEL,
            VENDOR_SENSOR_EXT_MODEL,
            SNMP_AGENT_MODEL
    );

    public static final String SNMP_DRIVER_CONFIG =
            "{\"host\":\"127.0.0.1\",\"port\":\"161\",\"community\":\"public\",\"version\":\"2c\",\"timeoutMs\":\"3000\",\"retries\":\"1\"}";

    /** OID mappings for {@link #SNMP_AGENT_MODEL} — must match dashboard {@code snmp-host-monitoring}. */
    public static final String SNMP_POINT_MAPPINGS =
            "{\"sysName\":\"1.3.6.1.2.1.1.5.0:STRING\",\"sysDescr\":\"1.3.6.1.2.1.1.1.0:STRING\","
                    + "\"sysUpTime\":\"1.3.6.1.2.1.1.3.0\",\"sysLocation\":\"1.3.6.1.2.1.1.6.0:STRING\","
                    + "\"sysContact\":\"1.3.6.1.2.1.1.4.0:STRING\","
                    + "\"hrMemorySize\":\"1.3.6.1.2.1.25.2.2.0:INTEGER\","
                    + "\"hrSystemProcesses\":\"1.3.6.1.2.1.25.1.6.0:INTEGER\","
                    + "\"hrSystemNumUsers\":\"1.3.6.1.2.1.25.1.5.0:INTEGER\","
                    + "\"ifNumber\":\"1.3.6.1.2.1.2.1.0:INTEGER\","
                    + "\"ifInOctets\":\"1.3.6.1.2.1.2.2.1.10.2:INTEGER\","
                    + "\"ifOutOctets\":\"1.3.6.1.2.1.2.2.1.16.2:INTEGER\","
                    + "\"ifDescr\":\"1.3.6.1.2.1.2.2.1.2.2:STRING\","
                    + "\"ifSpeed\":\"1.3.6.1.2.1.2.2.1.5.2:INTEGER\","
                    + "\"ifOperStatus\":\"1.3.6.1.2.1.2.2.1.8.2:INTEGER\","
                    + "\"ifInErrors\":\"1.3.6.1.2.1.2.2.1.14.2:INTEGER\","
                    + "\"ifOutErrors\":\"1.3.6.1.2.1.2.2.1.20.2:INTEGER\","
                    + "\"ifInUcastPkts\":\"1.3.6.1.2.1.2.2.1.11.2:INTEGER\","
                    + "\"ifOutUcastPkts\":\"1.3.6.1.2.1.2.2.1.17.2:INTEGER\","
                    + "\"hrProcessorLoad\":\"1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional\"}";

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema THRESHOLD_SCHEMA = DataSchema.builder("threshold")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema FUNCTION_RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema VOID_INPUT_SCHEMA = DataSchema.builder("voidInput").build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public FixtureBlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensureFixtureBlueprints() {
        if (blueprintRegistry.findByName(MQTT_SENSOR_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(buildMqttSensorModel());
        }
        if (blueprintRegistry.findByName(MQTT_GATEWAY_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildMqttGatewayModel());
        }
        if (blueprintRegistry.findByName(MQTT_GATEWAY_SENSOR_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildMqttGatewaySensorInstanceModel());
        }
        if (blueprintRegistry.findByName(DEVICE_DRIVER_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildDeviceDriverModel());
        }
        if (blueprintRegistry.findByName(BASE_SENSOR_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildBaseSensorModel());
        }
        if (blueprintRegistry.findByName(VENDOR_SENSOR_EXT_MODEL).isEmpty()) {
            String baseId = blueprintRegistry.requireByName(BASE_SENSOR_MODEL).id();
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildVendorSensorExtensionModel(baseId));
        }
        if (blueprintRegistry.findByName(SNMP_AGENT_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildSnmpAgentModel());
        }
        if (blueprintRegistry.findByName(METERS_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildMetersModel());
        }
        if (blueprintRegistry.findByName(MQTT_METER_BUS_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildMqttMeterBusModel());
        }
    }

    /**
     * Drops demo/lab model catalog nodes from the object tree and in-memory registry.
     */
    public void removeFixtureBlueprintsIfPresent(ObjectManager objectManager) {
        for (String name : FIXTURE_MODEL_NAMES) {
            blueprintRegistry.findByName(name).ifPresent(model -> {
                String path = model.catalogObjectPath();
                objectManager.tree().findByPath(path).ifPresent(node -> objectManager.delete(path));
                blueprintRegistry.delete(model.id());
            });
        }
    }

    public static BlueprintDefinition buildMqttSensorModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                MQTT_SENSOR_MODEL,
                "MQTT temperature sensor with threshold monitoring (demo fixture)",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.withHistory(
                                "temperature",
                                "Current temperature reading",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true, DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 22.5, "unit", "C"))
                        ),
                        BlueprintVariableDefinition.of(
                                "threshold",
                                "Alarm threshold in Celsius",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true, DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
                        ),
                        BlueprintVariableDefinition.of(
                                "temperaturePercent",
                                "Temperature normalized to 0-100% (-20..50 °C)",
                                "telemetry",
                                THRESHOLD_SCHEMA,
                                true,
                                false,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 0.0))
                        ),
                        BlueprintVariableDefinition.of(
                                "alarmActive",
                                "Whether temperature exceeds threshold",
                                "status",
                                DataSchema.builder("alarmActive").field("value", FieldType.BOOLEAN).build(),
                                true,
                                false,
                                DataRecord.single(
                                        DataSchema.builder("alarmActive").field("value", FieldType.BOOLEAN).build(),
                                        Map.of("value", false)
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "alarmAcknowledged",
                                "Operator acknowledged the active alarm",
                                "status",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
                        )
                ),
                List.of(new EventDescriptor(
                        "thresholdExceeded",
                        "Temperature exceeded configured threshold",
                        TEMPERATURE_SCHEMA,
                        EventLevel.WARNING
                )),
                List.of(new FunctionDescriptor(
                        "acknowledgeAlarm",
                        "Acknowledge active temperature alarm",
                        VOID_INPUT_SCHEMA,
                        FUNCTION_RESULT_SCHEMA
                )),
                List.of(BlueprintBindingRule.of("alarm-active", "alarmActive", "hysteresis(temperature, 35, 33)")),
                Map.of("unit", "C"),
                Instant.now(),
                Instant.now()
        );
    }
}
