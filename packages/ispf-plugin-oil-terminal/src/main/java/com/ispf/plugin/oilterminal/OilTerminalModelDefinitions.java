package com.ispf.plugin.oilterminal;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelBindingDefinition;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model definitions for oil terminal reference stand (P-301 / P-201 / P-210).
 */
public final class OilTerminalModelDefinitions {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private static final DataSchema FUNCTION_RESULT = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema VOID_INPUT = DataSchema.builder("voidInput").build();

    private static final DataSchema ASSIGN_INPUT = DataSchema.builder("assignInput")
            .field("tankName", FieldType.STRING)
            .field("rackName", FieldType.STRING)
            .build();

    private static final DataSchema COMPLETE_INPUT = DataSchema.builder("completeInput")
            .field("actualLiters", FieldType.DOUBLE)
            .build();

    private static final DataSchema DISPATCH_EVENT_PAYLOAD = DataSchema.builder("dispatchEventPayload")
            .field("orderNo", FieldType.STRING)
            .field("tankName", FieldType.STRING)
            .field("rackName", FieldType.STRING)
            .field("actualLiters", FieldType.DOUBLE)
            .build();

    private static final DataSchema TANK_LEVEL_PAYLOAD = DataSchema.builder("tankLevelPayload")
            .field("levelM3", FieldType.DOUBLE)
            .field("minLevelM3", FieldType.DOUBLE)
            .build();

    private static final DataSchema APPROVE_INPUT = DataSchema.builder("approveInput")
            .field("tankName", FieldType.STRING)
            .build();

    private static final String TANK_DRIVER_CONFIG =
            "{\"baseLevelM3\":\"1200\",\"levelAmplitudeM3\":\"8\",\"baseTemperature\":\"18\","
                    + "\"tempAmplitude\":\"2\",\"periodSec\":\"90\"}";

    private static final String TANK_POINT_MAPPINGS =
            "{\"levelM3\":\"sim-tank-level\",\"temperatureC\":\"sim-temperature\",\"status\":\"sim-status\"}";

    private OilTerminalModelDefinitions() {
    }

    public static ModelDefinition oilTank() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                OilTerminalConstants.MODEL_TANK,
                "Oil storage tank (РВС) with level monitoring",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        stringVar("productCode", "config", "DT"),
                        doubleVar("levelM3", "telemetry", 1200.0),
                        doubleVar("temperatureC", "telemetry", 18.0),
                        doubleVar("minLevelM3", "config", 50.0),
                        doubleVar("maxLevelM3", "config", 5000.0),
                        boolVar("qualityOk", "status", true),
                        boolVar("levelLow", "status", false),
                        statusVar(),
                        driverStringVar("driverId", "virtual"),
                        driverStringVar("driverStatus", "STOPPED"),
                        driverIntVar("driverPollIntervalMs", 2000),
                        driverStringVar("driverConfigJson", TANK_DRIVER_CONFIG),
                        driverStringVar("driverPointMappingsJson", TANK_POINT_MAPPINGS)
                ),
                List.of(new EventDescriptor(
                        OilTerminalConstants.EVENT_TANK_LEVEL_LOW,
                        "Tank level below minimum threshold",
                        TANK_LEVEL_PAYLOAD,
                        EventLevel.WARNING
                )),
                List.of(),
                List.of(
                        new ModelBindingDefinition(
                                "levelLow",
                                "self.levelM3[\"value\"] < self.minLevelM3[\"value\"]"
                        )
                ),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition oilRack() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                OilTerminalConstants.MODEL_RACK,
                "Loading rack (эстакада) with totalizer",
                ModelType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(
                        boolVar("busy", "status", false),
                        stringVar("currentOrderName", "runtime", null),
                        doubleVar("flowRateLpm", "telemetry", 800.0),
                        doubleVar("totalizerL", "telemetry", 0.0)
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition dispatchOrder() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                OilTerminalConstants.MODEL_DISPATCH,
                "Dispatch order (наряд на отгрузку)",
                ModelType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(
                        stringVar("orderNo", "info", ""),
                        stringVar("productCode", "info", "DT"),
                        doubleVar("plannedLiters", "info", 0.0),
                        doubleVar("actualLiters", "runtime", null),
                        stringVar("status", "runtime", DispatchStatus.PLANNED.wireValue()),
                        stringVar("tankName", "runtime", null),
                        stringVar("rackName", "runtime", null),
                        stringVar("vehiclePlate", "info", null),
                        stringVar("startedAt", "runtime", null),
                        stringVar("finishedAt", "runtime", null)
                ),
                List.of(
                        new EventDescriptor(
                                OilTerminalConstants.EVENT_DISPATCH_STARTED,
                                "Dispatch filling started",
                                DISPATCH_EVENT_PAYLOAD,
                                EventLevel.INFO
                        ),
                        new EventDescriptor(
                                OilTerminalConstants.EVENT_DISPATCH_COMPLETED,
                                "Dispatch filling completed",
                                DISPATCH_EVENT_PAYLOAD,
                                EventLevel.INFO
                        ),
                        new EventDescriptor(
                                OilTerminalConstants.EVENT_DISPATCH_CANCELLED,
                                "Dispatch cancelled",
                                DISPATCH_EVENT_PAYLOAD,
                                EventLevel.WARNING
                        )
                ),
                List.of(
                        new FunctionDescriptor("assign", "Assign tank and rack", ASSIGN_INPUT, FUNCTION_RESULT),
                        new FunctionDescriptor("start", "Start filling", VOID_INPUT, FUNCTION_RESULT),
                        new FunctionDescriptor("complete", "Complete filling", COMPLETE_INPUT, FUNCTION_RESULT),
                        new FunctionDescriptor("close", "Close order after ERP sync", VOID_INPUT, FUNCTION_RESULT)
                ),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    public static ModelDefinition oilSample() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                OilTerminalConstants.MODEL_SAMPLE,
                "Lab sample for tank quality approval (P-210)",
                ModelType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(
                        stringVar("tankName", "info", null),
                        stringVar("sampleNo", "info", ""),
                        boolVar("approved", "status", false)
                ),
                List.of(new EventDescriptor(
                        OilTerminalConstants.EVENT_LAB_APPROVED,
                        "Lab sample approved — tank released",
                        DataSchema.builder("labApprovedPayload")
                                .field("tankName", FieldType.STRING)
                                .field("sampleNo", FieldType.STRING)
                                .build(),
                        EventLevel.INFO
                )),
                List.of(new FunctionDescriptor("approve", "Approve sample and release tank", APPROVE_INPUT, FUNCTION_RESULT)),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelVariableDefinition stringVar(String name, String group, String defaultValue) {
        return new ModelVariableDefinition(
                name,
                name,
                group,
                STRING_VALUE,
                true,
                true,
                null,
                DataRecord.single(STRING_VALUE, Map.of("value", defaultValue != null ? defaultValue : ""))
        );
    }

    private static ModelVariableDefinition doubleVar(String name, String group, Double defaultValue) {
        return new ModelVariableDefinition(
                name,
                name,
                group,
                DOUBLE_VALUE,
                true,
                true,
                null,
                defaultValue != null
                        ? DataRecord.single(DOUBLE_VALUE, Map.of("value", defaultValue))
                        : null
        );
    }

    private static ModelVariableDefinition boolVar(String name, String group, boolean defaultValue) {
        return new ModelVariableDefinition(
                name,
                name,
                group,
                BOOLEAN_VALUE,
                true,
                true,
                null,
                DataRecord.single(BOOLEAN_VALUE, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition statusVar() {
        return new ModelVariableDefinition(
                "status",
                "Device connectivity status",
                "status",
                STATUS_SCHEMA,
                true,
                false,
                null,
                DataRecord.single(STATUS_SCHEMA, Map.of("online", true, "lastSeen", ""))
        );
    }

    private static ModelVariableDefinition driverStringVar(String name, String defaultValue) {
        return new ModelVariableDefinition(
                name,
                name,
                "driver",
                STRING_VALUE,
                true,
                name.equals("driverStatus") ? false : true,
                null,
                DataRecord.single(STRING_VALUE, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition driverIntVar(String name, int defaultValue) {
        return new ModelVariableDefinition(
                name,
                name,
                "driver",
                INTEGER_VALUE,
                true,
                true,
                null,
                DataRecord.single(INTEGER_VALUE, Map.of("value", defaultValue))
        );
    }
}
