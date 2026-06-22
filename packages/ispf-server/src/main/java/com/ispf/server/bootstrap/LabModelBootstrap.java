package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelBindingDefinition;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LabModelBootstrap {

    public static final String VIRTUAL_LAB_MODEL = "virtual-lab-v1";
    public static final String VIRTUAL_LAB_WAVES_SUM_MODEL = "virtual-lab-waves-sum-v1";
    public static final String TREE_VARIABLES_REPORT_MODEL = "tree-variables-report-v1";
    public static final String TREE_VARIABLES_REPORT_TYPE = "tree-variables";

    public static final String LAB_DRIVER_CONFIG =
            "{\"profile\":\"lab\",\"sineAmplitude\":\"10.0\",\"sawtoothAmplitude\":\"5.0\","
                    + "\"triangleAmplitude\":\"5.0\",\"periodSec\":\"60\"}";

    public static final String LAB_POINT_MAPPINGS =
            "{\"sineWave\":\"sim\",\"sawtoothWave\":\"sim\",\"triangleWave\":\"sim\",\"status\":\"sim\"}";

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private static final DataSchema DOUBLE_VALUE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema FUNCTION_RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema CALCULATE_INPUT_SCHEMA = DataSchema.builder("calculateInput")
            .field("inputA", FieldType.DOUBLE)
            .field("inputB", FieldType.DOUBLE)
            .build();

    private static final DataSchema CALCULATE_OUTPUT_SCHEMA = DataSchema.builder("calculateOutput")
            .field("result", FieldType.DOUBLE)
            .build();

    private static final DataSchema EVENT_INPUT_SCHEMA = DataSchema.builder("eventInput")
            .field("int", FieldType.INTEGER)
            .field("string", FieldType.STRING)
            .build();

    private static final DataSchema EVENT_PAYLOAD_SCHEMA = DataSchema.builder("eventPayload")
            .field("int", FieldType.INTEGER)
            .field("string", FieldType.STRING)
            .build();

    private static final DataSchema TABLE_ROW_SCHEMA = DataSchema.builder("tableRow")
            .field("int", FieldType.INTEGER)
            .field("string", FieldType.STRING)
            .build();

    private static final DataSchema TABLE_SCHEMA = DataSchema.builder("table")
            .field(new FieldDefinition("rows", FieldType.RECORD_LIST, "", true, TABLE_ROW_SCHEMA))
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public LabModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureLabModels() {
        ensureModel(VIRTUAL_LAB_MODEL, buildVirtualLabModel());
        ensureModel(VIRTUAL_LAB_WAVES_SUM_MODEL, buildVirtualLabWavesSumModel());
        ensureModel(TREE_VARIABLES_REPORT_MODEL, buildTreeVariablesReportModel());
    }

    private void ensureModel(String name, ModelDefinition definition) {
        if (modelRegistry.findByName(name).isEmpty()) {
            modelEngine.createModel(definition);
        }
    }

    private static ModelDefinition buildVirtualLabModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                VIRTUAL_LAB_MODEL,
                "Virtual lab device — waves, writable vars, table, events, and functions",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        varDef("status", "Device connectivity status", "status", STATUS_SCHEMA,
                                DataRecord.single(STATUS_SCHEMA, Map.of("online", true, "lastSeen", "init"))),
                        telemetryHistoryDef("sineWave", "Sine wave telemetry", 0.0),
                        telemetryHistoryDef("sawtoothWave", "Sawtooth wave telemetry", 0.0),
                        telemetryHistoryDef("triangleWave", "Triangle wave telemetry", 0.0),
                        intDef("intValue", "Writable integer (model-managed)", "config", 0),
                        doubleDef("floatValue", "Writable float (model-managed)", "config", 0.0),
                        doubleDef("sumWaves", "Sum of sine and sawtooth waves", "telemetry", 0.0),
                        doubleDef("sumIntFloat", "Sum of intValue and floatValue", "telemetry", 0.0),
                        intDef("tableIntSum", "Sum of int column in table", "telemetry", 0),
                        tableDef("table", "Appendable table rows"),
                        boolDef("alarmLatched", "Latched alarm state", false),
                        boolDef("fanRunning", "Fan running state", false),
                        varDef("driverId", "Attached driver plugin id", "driver", STRING_VALUE_SCHEMA,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "virtual"))),
                        varDef("driverStatus", "Driver runtime status", "driver", STRING_VALUE_SCHEMA,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", "STOPPED"))),
                        intDef("driverPollIntervalMs", "Driver polling interval", "driver", 2000),
                        varDef("driverConfigJson", "Driver configuration JSON", "driver", STRING_VALUE_SCHEMA,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", LAB_DRIVER_CONFIG))),
                        varDef("driverPointMappingsJson", "Driver point mappings JSON", "driver", STRING_VALUE_SCHEMA,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", LAB_POINT_MAPPINGS)))
                ),
                List.of(
                        new EventDescriptor("event1", "Lab event 1", EVENT_PAYLOAD_SCHEMA, EventLevel.INFO),
                        new EventDescriptor("event2", "Lab event 2", EVENT_PAYLOAD_SCHEMA, EventLevel.INFO)
                ),
                List.of(
                        new FunctionDescriptor(
                                "calculate",
                                "Add inputA and inputB",
                                CALCULATE_INPUT_SCHEMA,
                                CALCULATE_OUTPUT_SCHEMA
                        ),
                        new FunctionDescriptor(
                                "fireEvent1",
                                "Fire event1 with int/string payload",
                                EVENT_INPUT_SCHEMA,
                                FUNCTION_RESULT_SCHEMA
                        ),
                        new FunctionDescriptor(
                                "fireEvent2",
                                "Fire event2 with int/string payload",
                                EVENT_INPUT_SCHEMA,
                                FUNCTION_RESULT_SCHEMA
                        ),
                        new FunctionDescriptor(
                                "appendTableRow",
                                "Append a row to the table variable",
                                EVENT_INPUT_SCHEMA,
                                FUNCTION_RESULT_SCHEMA
                        )
                ),
                List.of(
                        new ModelBindingDefinition(
                                "sumIntFloat",
                                "self.intValue.value + self.floatValue.value"
                        ),
                        new ModelBindingDefinition(
                                "tableIntSum",
                                "sumRecordField(table, int)"
                        )
                ),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelVariableDefinition writableStringDef(
            String name,
            String description,
            String group,
            String defaultValue
    ) {
        return ModelVariableDefinition.of(
                name,
                description,
                group,
                STRING_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelDefinition buildTreeVariablesReportModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                TREE_VARIABLES_REPORT_MODEL,
                "Tree-variables report — scan devices by path pattern and flatten RECORD_LIST rows",
                ModelType.RELATIVE,
                ObjectType.REPORT,
                "",
                List.of(
                        writableStringDef("title", "Report title", "info", "Tree variables report"),
                        writableStringDef("reportType", "Report source type", "config", TREE_VARIABLES_REPORT_TYPE),
                        writableStringDef("devicePathPattern", "Device path prefix or glob (supports *)", "config",
                                "root.platform.devices."),
                        writableStringDef("variableName", "Variable to read on each matching device", "config", "table"),
                        writableStringDef("columns", "JSON array of {field, label}", "config",
                                "[{\"field\":\"devicepath\",\"label\":\"Device path\"},{\"field\":\"int\",\"label\":\"Int\"},{\"field\":\"string\",\"label\":\"String\"}]"),
                        writableStringDef("defaultParameters", "Unused for tree-variables reports", "config", "{}"),
                        intDef("maxRows", "Maximum rows returned", "config", 1000),
                        intDef("refreshIntervalMs", "Auto-refresh interval in view mode", "config", 30000),
                        writableStringDef("templateFormat", "YARG template format (empty = none)", "config", ""),
                        writableStringDef("layout", "Report layout JSON", "config", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition buildVirtualLabWavesSumModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                VIRTUAL_LAB_WAVES_SUM_MODEL,
                "Virtual lab waves sum — adds sumWaves binding only",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(new ModelBindingDefinition(
                        "sumWaves",
                        "self.sineWave.value + self.sawtoothWave.value"
                )),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelVariableDefinition varDef(
            String name,
            String description,
            String group,
            DataSchema schema,
            DataRecord defaultValue
    ) {
        return ModelVariableDefinition.of(name, description, group, schema, true, false, null, defaultValue);
    }

    private static ModelVariableDefinition doubleDef(String name, String description, String group, double defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                group,
                DOUBLE_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(DOUBLE_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition telemetryHistoryDef(String name, String description, double defaultValue) {
        return ModelVariableDefinition.withHistory(
                name,
                description,
                "telemetry",
                DOUBLE_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(DOUBLE_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition intDef(String name, String description, int defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                "config",
                INTEGER_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition intDef(String name, String description, String group, int defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                group,
                INTEGER_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition boolDef(String name, String description, boolean defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                "status",
                BOOLEAN_VALUE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition tableDef(String name, String description) {
        return ModelVariableDefinition.of(
                name,
                description,
                "telemetry",
                TABLE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(TABLE_SCHEMA, Map.of("rows", List.of()))
        );
    }
}
