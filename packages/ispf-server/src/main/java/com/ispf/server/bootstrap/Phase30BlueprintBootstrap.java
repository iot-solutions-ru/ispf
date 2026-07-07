package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class Phase30BlueprintBootstrap {

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public Phase30BlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensurePhase30Models() {
        ensureModel("query-v1", buildQueryModel());
        ensureModel("event-filter-v1", buildEventFilterModel());
        ensureModel("process-program-v1", buildProcessProgramModel());
    }

    private void ensureModel(String name, BlueprintDefinition definition) {
        BlueprintDefinition intrinsic = definition.withSystemIntrinsicFlag();
        blueprintRegistry.findByName(name).ifPresentOrElse(
                existing -> {
                    if (!existing.systemIntrinsic()) {
                        blueprintEngine.updateBlueprint(existing.withSystemIntrinsicFlag());
                    }
                },
                () -> blueprintEngine.createBlueprint(intrinsic)
        );
    }

    private static BlueprintDefinition buildQueryModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "query-v1",
                "Cross-object query definition (tree scan or SQL)",
                BlueprintType.RELATIVE,
                ObjectType.QUERY,
                "",
                List.of(
                        varDef("queryId", "Stable query id", "info", ""),
                        varDef("queryType", "Query type: tree-scan|sql", "config", "tree-scan"),
                        varDef("sourcePathPattern", "Object path glob", "config", "root.platform.devices.*"),
                        varDef("fieldsJson", "Output fields JSON array", "config", "[]"),
                        varDef("filterExpression", "CEL filter on variables", "config", ""),
                        boolDef("enabled", "Enabled", true),
                        varDef("lastRunAt", "Last run ISO instant", "runtime", ""),
                        varDef("lastError", "Last error message", "runtime", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition buildEventFilterModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "event-filter-v1",
                "Reusable event journal filter",
                BlueprintType.RELATIVE,
                ObjectType.EVENT_FILTER,
                "",
                List.of(
                        varDef("filterId", "Stable filter id", "info", ""),
                        varDef("eventNamePattern", "Event name glob", "config", "*"),
                        varDef("sourceObjectPathPattern", "Source object path glob", "config", "root.platform.**"),
                        intDef("minSeverity", "Minimum severity (inclusive)", 0),
                        intDef("maxSeverity", "Maximum severity (inclusive)", 100),
                        intDef("timeWindowMs", "Sliding window ms (0 = all time)", 0),
                        varDef("filterExpression", "CEL expression on event fields", "config", ""),
                        boolDef("enabled", "Enabled", true)
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition buildProcessProgramModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "process-program-v1",
                "Cyclic process-control loop program (BL-172)",
                BlueprintType.RELATIVE,
                ObjectType.PROCESS_PROGRAM,
                "",
                List.of(
                        varDef("programId", "Stable program id", "info", ""),
                        intDef("cycleIntervalMs", "Control loop interval ms", 1000),
                        varDef("controlExpression", "CEL expression evaluated each cycle", "config", ""),
                        boolDef("enabled", "Program enabled", false),
                        varDef("lastCycleAt", "Last cycle ISO instant", "runtime", ""),
                        varDef("lastError", "Last cycle error message", "runtime", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintVariableDefinition varDef(String name, String description, String group, String defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                group,
                STRING_VALUE_SCHEMA,
                true,
                true,
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static BlueprintVariableDefinition intDef(String name, String description, int defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                "config",
                INTEGER_VALUE_SCHEMA,
                true,
                true,
                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static BlueprintVariableDefinition boolDef(String name, String description, boolean defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                "config",
                BOOLEAN_VALUE_SCHEMA,
                true,
                true,
                DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }
}
