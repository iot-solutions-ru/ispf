package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.server.function.MqttGatewayFunctionHandler;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.binding.BindingActivators;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Static model blueprints for platform fixtures (demo / lab only). */
public final class FixtureBlueprintDefinitions {

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema THRESHOLD_SCHEMA = DataSchema.builder("threshold")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema SNMP_NUMERIC_SCHEMA = DataSchema.builder("snmpNumeric")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DataSchema SNMP_STRING_SCHEMA = DataSchema.builder("snmpString")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DataSchema MQTT_INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DataSchema DISPATCH_STATUS_SCHEMA = DataSchema.builder("dispatchStatus")
            .field("ok", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .field("routedPath", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema FUNCTION_RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema VOID_INPUT_SCHEMA = DataSchema.builder("voidInput").build();

    private FixtureBlueprintDefinitions() {
    }

    public static BlueprintDefinition buildMqttGatewayModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                DemoFixtureBootstrap.MQTT_GATEWAY_MODEL,
                "MQTT ingress gateway — routes lastIngress to child sensors via dispatchTelemetry",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "lastIngress",
                                "Latest MQTT message (topic + raw payload)",
                                "ingress",
                                MQTT_INGRESS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(MQTT_INGRESS_SCHEMA, Map.of("topic", "", "raw", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "dispatchStatus",
                                "Last dispatchTelemetry result",
                                "runtime",
                                DISPATCH_STATUS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(DISPATCH_STATUS_SCHEMA, Map.of("ok", false, "message", "", "routedPath", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sensorParentPath",
                                "Parent path for routed child sensor instances",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", "root.platform.instances")
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "sensorNamePrefix",
                                "Child sensor object name prefix (suffix from topic index)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "loadtest-mqtt-sensor-"))
                        ),
                        BlueprintVariableDefinition.of(
                                "topicIndexPattern",
                                "Regex with capture group for sensor index in MQTT topic",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", "ispf/loadtest/(\\d+)/temperature")
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "instanceModelName",
                                "INSTANCE type name for auto-created gateway child sensors",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", PlatformReferenceBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL)
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "mqtt"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", "{\"ingressVariable\":\"lastIngress\"}")
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", "{\"ingress\":\"ispf/loadtest/+/temperature\"}")
                                )
                        )
                ),
                List.of(),
                List.of(new FunctionDescriptor(
                        MqttGatewayFunctionHandler.FUNCTION_NAME,
                        "Route lastIngress MQTT payload to a child sensor object",
                        MQTT_INGRESS_SCHEMA,
                        DISPATCH_STATUS_SCHEMA
                )),
                List.of(new BlueprintBindingRule(
                        "dispatch-on-ingress",
                        "Dispatch ingress to child sensor",
                        true,
                        10,
                        new BindingActivators(
                                false,
                                List.of(BindingVariableRef.local("lastIngress")),
                                null,
                                0,
                                true
                        ),
                        "",
                        "call(@/fn/dispatchTelemetry, @/lastIngress)",
                        "dispatchStatus",
                        "ok"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildDeviceDriverModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                DemoFixtureBootstrap.DEVICE_DRIVER_MODEL,
                "Generic device with driver binding (driverId, config, mappings)",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false, DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverAutoStart",
                                "Start this device driver automatically when the server boots (default true)",
                                "driver",
                                BOOLEAN_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", true))
                        ),
                        BlueprintVariableDefinition.of(
                                "timeZone",
                                "IANA timezone for device-local timestamps (empty = inherit from parent or UTC)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildMqttGatewaySensorInstanceModel() {
        return new BlueprintDefinition(
                PlatformReferenceBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL_ID,
                PlatformReferenceBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL,
                "MQTT temperature sensor — child of mqtt-gateway-v1 (telemetry via dispatchTelemetry, threshold alarms)",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(
                        BlueprintVariableDefinition.withHistory(
                                "temperature",
                                "Current temperature reading",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 22.0, "unit", "C"))
                        ),
                        BlueprintVariableDefinition.of(
                                "threshold",
                                "Alarm threshold in Celsius",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true,
                                DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
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
                                true,
                                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", false))
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

    public static BlueprintDefinition buildMetersModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                DemoFixtureBootstrap.METERS_MODEL,
                "MQTT meter instance — temperature telemetry",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(
                        BlueprintVariableDefinition.withHistory(
                                "temperature",
                                "Current temperature reading",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 22.0, "unit", "C"))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of("unit", "C"),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildMqttMeterBusModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                DemoFixtureBootstrap.MQTT_METER_BUS_MODEL,
                "MQTT meter bus — ingests JSON meter payloads and upserts Meters instances",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "lastIngress",
                                "Latest MQTT message (topic + raw payload)",
                                "ingress",
                                MQTT_INGRESS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(MQTT_INGRESS_SCHEMA, Map.of("topic", "", "raw", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "ingestStatus",
                                "Last ingestMeterPayload result",
                                "runtime",
                                DISPATCH_STATUS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(DISPATCH_STATUS_SCHEMA, Map.of("ok", false, "message", "", "routedPath", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "instanceParentPath",
                                "Parent path for auto-created meter instances",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "root.platform.instances"))
                        ),
                        BlueprintVariableDefinition.of(
                                "instanceModelName",
                                "INSTANCE model name for auto-created meters",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DemoFixtureBootstrap.METERS_MODEL))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "mqtt"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", DemoFixtureBootstrap.METER_INGRESS_DRIVER_CONFIG)
                                )
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", DemoFixtureBootstrap.METER_INGRESS_POINT_MAPPINGS)
                                )
                        )
                ),
                List.of(),
                List.of(new FunctionDescriptor(
                        DemoFixtureBootstrap.INGEST_METER_PAYLOAD_FUNCTION,
                        "Parse meter JSON from MQTT ingress and upsert Meters instance",
                        MQTT_INGRESS_SCHEMA,
                        DISPATCH_STATUS_SCHEMA,
                        "script",
                        DemoFixtureBootstrap.METER_INGEST_SCRIPT_BODY,
                        null,
                        "1"
                )),
                List.of(new BlueprintBindingRule(
                        "ingest-on-ingress",
                        "Ingest meter payload from MQTT",
                        true,
                        10,
                        new BindingActivators(
                                false,
                                List.of(BindingVariableRef.local("lastIngress")),
                                null,
                                0,
                                true
                        ),
                        "",
                        "call(@/fn/" + DemoFixtureBootstrap.INGEST_METER_PAYLOAD_FUNCTION + ", @/lastIngress)",
                        "ingestStatus",
                        "ok"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildBaseSensorModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "base-sensor-v1",
                "Base temperature sensor — family blueprint",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "temperature",
                                "Current temperature",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true, DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 20.0, "unit", "C"))
                        ),
                        BlueprintVariableDefinition.of(
                                "threshold",
                                "Alarm threshold",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true, DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 35.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of("blueprintVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildVendorSensorExtensionModel(String baseModelId) {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "vendor-sensor-ext-v1",
                "Vendor extension — adds humidity without duplicating base sensor",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "humidity",
                                "Relative humidity percent",
                                "telemetry",
                                THRESHOLD_SCHEMA,
                                true,
                                true, DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 45.0))
                        ),
                        BlueprintVariableDefinition.of(
                                "threshold",
                                "Vendor-specific threshold override",
                                "config",
                                THRESHOLD_SCHEMA,
                                true,
                                true, DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 40.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of("extendsBlueprintId", baseModelId, "blueprintVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    public static BlueprintDefinition buildSnmpAgentModel() {
        return new BlueprintDefinition(
                PlatformReferenceBlueprintBootstrap.SNMP_AGENT_MODEL_ID,
                PlatformReferenceBlueprintBootstrap.SNMP_AGENT_MODEL,
                "SNMP agent device (MIB-II system group)",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false, DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sysName",
                                "SNMP sysName (1.3.6.1.2.1.1.5.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sysDescr",
                                "SNMP sysDescr (1.3.6.1.2.1.1.1.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "sysUpTime",
                                "SNMP sysUpTime (1.3.6.1.2.1.1.3.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sysLocation",
                                "SNMP sysLocation (1.3.6.1.2.1.1.6.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "sysContact",
                                "SNMP sysContact (1.3.6.1.2.1.1.4.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "hrMemorySize",
                                "Physical memory size in KB (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.2.2.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "hrSystemProcesses",
                                "Running processes (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.6.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "hrSystemNumUsers",
                                "Logged-in users (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.5.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "ifNumber",
                                "Network interfaces count (IF-MIB 1.3.6.1.2.1.2.1.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "ifInOctets",
                                "IF-MIB ifInOctets Counter32 — total octets received on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "ifOutOctets",
                                "IF-MIB ifOutOctets Counter32 — total octets sent on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "ifInOctetsRate",
                                "Inbound traffic rate B/s derived from ifInOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "ifOutOctetsRate",
                                "Outbound traffic rate B/s derived from ifOutOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.withHistory(
                                "hrProcessorLoad",
                                "CPU load % (HOST-RESOURCES-MIB hrProcessorLoad, Linux index 196608)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "snmp"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DemoFixtureBootstrap.SNMP_DRIVER_CONFIG))
                        ),
                        BlueprintVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", DemoFixtureBootstrap.SNMP_POINT_MAPPINGS))
                        )
                ),
                List.of(),
                List.of(),
                List.of(
                        BlueprintBindingRule.of("if-in-octets-rate", "ifInOctetsRate", "counterRate(ifInOctets)"),
                        BlueprintBindingRule.of("if-out-octets-rate", "ifOutOctetsRate", "counterRate(ifOutOctets)")
                ),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }


}
