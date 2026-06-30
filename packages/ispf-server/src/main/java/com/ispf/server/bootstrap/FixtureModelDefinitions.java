package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.function.MqttGatewayFunctionHandler;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.binding.BindingActivators;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Static model blueprints for platform fixtures (demo / lab only). */
public final class FixtureModelDefinitions {

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

    private FixtureModelDefinitions() {
    }

    public static ModelDefinition buildMqttGatewayModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                FixtureModelBootstrap.MQTT_GATEWAY_MODEL,
                "MQTT ingress gateway — routes lastIngress to child sensors via dispatchTelemetry",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "lastIngress",
                                "Latest MQTT message (topic + raw payload)",
                                "ingress",
                                MQTT_INGRESS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(MQTT_INGRESS_SCHEMA, Map.of("topic", "", "raw", ""))
                        ),
                        ModelVariableDefinition.of(
                                "dispatchStatus",
                                "Last dispatchTelemetry result",
                                "runtime",
                                DISPATCH_STATUS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(DISPATCH_STATUS_SCHEMA, Map.of("ok", false, "message", "", "routedPath", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sensorParentPath",
                                "Parent path for routed child sensors",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sensorNamePrefix",
                                "Child sensor object name prefix (suffix from topic index)",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "loadtest-mqtt-sensor-"))
                        ),
                        ModelVariableDefinition.of(
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
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "mqtt"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
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
                        ModelVariableDefinition.of(
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
                List.of(new ModelBindingRule(
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
                        "callFunction(dispatchTelemetry, lastIngress)",
                        "dispatchStatus",
                        "ok"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition buildDeviceDriverModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                FixtureModelBootstrap.DEVICE_DRIVER_MODEL,
                "Generic device with driver binding (driverId, config, mappings)",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false, DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "{}"))
                        ),
                        ModelVariableDefinition.of(
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

    public static ModelDefinition buildMetersModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                FixtureModelBootstrap.METERS_MODEL,
                "MQTT meter instance — temperature telemetry",
                ModelType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(
                        ModelVariableDefinition.withHistory(
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

    public static ModelDefinition buildMqttMeterBusModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                FixtureModelBootstrap.MQTT_METER_BUS_MODEL,
                "MQTT meter bus — ingests JSON meter payloads and upserts Meters instances",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "lastIngress",
                                "Latest MQTT message (topic + raw payload)",
                                "ingress",
                                MQTT_INGRESS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(MQTT_INGRESS_SCHEMA, Map.of("topic", "", "raw", ""))
                        ),
                        ModelVariableDefinition.of(
                                "ingestStatus",
                                "Last ingestMeterPayload result",
                                "runtime",
                                DISPATCH_STATUS_SCHEMA,
                                true,
                                false,
                                DataRecord.single(DISPATCH_STATUS_SCHEMA, Map.of("ok", false, "message", "", "routedPath", ""))
                        ),
                        ModelVariableDefinition.of(
                                "instanceParentPath",
                                "Parent path for auto-created meter instances",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "root.platform.instances"))
                        ),
                        ModelVariableDefinition.of(
                                "instanceModelName",
                                "INSTANCE model name for auto-created meters",
                                "config",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", FixtureModelBootstrap.METERS_MODEL))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "mqtt"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", FixtureModelBootstrap.METER_INGRESS_DRIVER_CONFIG)
                                )
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(
                                        STRING_VALUE_SCHEMA,
                                        Map.of("value", FixtureModelBootstrap.METER_INGRESS_POINT_MAPPINGS)
                                )
                        )
                ),
                List.of(),
                List.of(new FunctionDescriptor(
                        FixtureModelBootstrap.INGEST_METER_PAYLOAD_FUNCTION,
                        "Parse meter JSON from MQTT ingress and upsert Meters instance",
                        MQTT_INGRESS_SCHEMA,
                        DISPATCH_STATUS_SCHEMA,
                        "script",
                        FixtureModelBootstrap.METER_INGEST_SCRIPT_BODY,
                        null,
                        "1"
                )),
                List.of(new ModelBindingRule(
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
                        "callFunction(" + FixtureModelBootstrap.INGEST_METER_PAYLOAD_FUNCTION + ", lastIngress)",
                        "ingestStatus",
                        "ok"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition buildBaseSensorModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "base-sensor-v1",
                "Base temperature sensor — family blueprint",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "temperature",
                                "Current temperature",
                                "telemetry",
                                TEMPERATURE_SCHEMA,
                                true,
                                true, DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 20.0, "unit", "C"))
                        ),
                        ModelVariableDefinition.of(
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
                Map.of("modelVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition buildVendorSensorExtensionModel(String baseModelId) {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "vendor-sensor-ext-v1",
                "Vendor extension — adds humidity without duplicating base sensor",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "humidity",
                                "Relative humidity percent",
                                "telemetry",
                                THRESHOLD_SCHEMA,
                                true,
                                true, DataRecord.single(THRESHOLD_SCHEMA, Map.of("value", 45.0))
                        ),
                        ModelVariableDefinition.of(
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
                Map.of("extendsModelId", baseModelId, "modelVersion", "1"),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition buildSnmpAgentModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                FixtureModelBootstrap.SNMP_AGENT_MODEL,
                "SNMP agent device (MIB-II system group)",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
                                "status",
                                "Device connectivity status",
                                "status",
                                STATUS_SCHEMA,
                                true,
                                false, DataRecord.single(STATUS_SCHEMA, Map.of("online", false, "lastSeen", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysName",
                                "SNMP sysName (1.3.6.1.2.1.1.5.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysDescr",
                                "SNMP sysDescr (1.3.6.1.2.1.1.1.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "sysUpTime",
                                "SNMP sysUpTime (1.3.6.1.2.1.1.3.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysLocation",
                                "SNMP sysLocation (1.3.6.1.2.1.1.6.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "sysContact",
                                "SNMP sysContact (1.3.6.1.2.1.1.4.0)",
                                "telemetry",
                                SNMP_STRING_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_STRING_SCHEMA, Map.of("value", "", "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrMemorySize",
                                "Physical memory size in KB (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.2.2.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrSystemProcesses",
                                "Running processes (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.6.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrSystemNumUsers",
                                "Logged-in users (HOST-RESOURCES-MIB 1.3.6.1.2.1.25.1.5.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifNumber",
                                "Network interfaces count (IF-MIB 1.3.6.1.2.1.2.1.0)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifInOctets",
                                "IF-MIB ifInOctets Counter32 — total octets received on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifOutOctets",
                                "IF-MIB ifOutOctets Counter32 — total octets sent on interface (monotonic, wraps at 2^32)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifInOctetsRate",
                                "Inbound traffic rate B/s derived from ifInOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "ifOutOctetsRate",
                                "Outbound traffic rate B/s derived from ifOutOctets Counter32",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                false,
                                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.withHistory(
                                "hrProcessorLoad",
                                "CPU load % (HOST-RESOURCES-MIB hrProcessorLoad, Linux index 196608)",
                                "telemetry",
                                SNMP_NUMERIC_SCHEMA,
                                true,
                                true, DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
                        ),
                        ModelVariableDefinition.of(
                                "driverId",
                                "Attached driver plugin id",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "snmp"))
                        ),
                        ModelVariableDefinition.of(
                                "driverStatus",
                                "Driver runtime status",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                false, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))
                        ),
                        ModelVariableDefinition.of(
                                "driverPollIntervalMs",
                                "Driver polling interval",
                                "driver",
                                INTEGER_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", 5000))
                        ),
                        ModelVariableDefinition.of(
                                "driverConfigJson",
                                "Driver configuration JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", FixtureModelBootstrap.SNMP_DRIVER_CONFIG))
                        ),
                        ModelVariableDefinition.of(
                                "driverPointMappingsJson",
                                "Driver point mappings JSON",
                                "driver",
                                STRING_VALUE_SCHEMA,
                                true,
                                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", FixtureModelBootstrap.SNMP_POINT_MAPPINGS))
                        )
                ),
                List.of(),
                List.of(),
                List.of(
                        ModelBindingRule.of("if-in-octets-rate", "ifInOctetsRate", "counterRate(ifInOctets)"),
                        ModelBindingRule.of("if-out-octets-rate", "ifOutOctetsRate", "counterRate(ifOutOctets)")
                ),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }


}
