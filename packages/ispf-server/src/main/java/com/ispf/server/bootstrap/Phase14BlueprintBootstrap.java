package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.plugin.model.SystemIntrinsicModels;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class Phase14ModelBootstrap {

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_VALUE_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public Phase14ModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensurePhase14Models() {
        ensureModel("data-source-v1", buildDataSourceModel());
        ensureModel("schedule-v1", buildScheduleModel());
        ensureModel("sql-binding-v1", buildSqlBindingModel());
        ensureModel("migration-v1", buildMigrationModel());
    }

    private void ensureModel(String name, ModelDefinition definition) {
        ModelDefinition intrinsic = definition.withSystemIntrinsicFlag();
        modelRegistry.findByName(name).ifPresentOrElse(
                existing -> {
                    if (!existing.systemIntrinsic()) {
                        modelEngine.updateModel(existing.withSystemIntrinsicFlag());
                    }
                },
                () -> modelEngine.createModel(intrinsic)
        );
    }

    private static ModelDefinition buildDataSourceModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "data-source-v1",
                "SQL schema reference for reports, bindings, and script functions",
                ModelType.RELATIVE,
                ObjectType.DATA_SOURCE,
                "",
                List.of(
                        varDef("displayName", "Display name", "info", ""),
                        varDef("schemaName", "PostgreSQL schema name", "config", "public")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition buildScheduleModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "schedule-v1",
                "Platform schedule — invoke function on interval",
                ModelType.RELATIVE,
                ObjectType.SCHEDULE,
                "",
                List.of(
                        varDef("scheduleId", "Stable schedule id", "info", ""),
                        boolDef("enabled", "Enabled", true),
                        intDef("intervalMs", "Tick interval ms", 60000),
                        varDef("actionType", "Action type", "config", "invoke_function"),
                        varDef("actionJson", "Action JSON", "config", "{}"),
                        varDef("lastTickAt", "Last tick ISO instant", "runtime", ""),
                        varDef("lastError", "Last error message", "runtime", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition buildSqlBindingModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "sql-binding-v1",
                "SQL query result synced to object variable",
                ModelType.RELATIVE,
                ObjectType.BINDING,
                "",
                List.of(
                        varDef("targetObjectPath", "Target object path", "config", ""),
                        varDef("variable", "Target variable name", "config", "value"),
                        varDef("dataSourcePath", "Data source object path", "config", ""),
                        varDef("query", "SELECT query", "config", ""),
                        varDef("valueField", "Result column", "config", "value"),
                        varDef("refresh", "Refresh mode: on_schedule|on_function_success|manual", "config", "manual"),
                        intDef("refreshIntervalMs", "Schedule refresh interval ms", 30000),
                        varDef("triggerObjectPath", "Trigger object for on_function_success", "config", ""),
                        varDef("triggerFunctionName", "Trigger function name", "config", ""),
                        boolDef("enabled", "Enabled", true),
                        varDef("lastRefreshedAt", "Last refresh ISO instant", "runtime", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelDefinition buildMigrationModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                "migration-v1",
                "SQL migration script applied on package import",
                ModelType.RELATIVE,
                ObjectType.MIGRATION,
                "",
                List.of(
                        varDef("scriptId", "Script id", "info", ""),
                        varDef("version", "Package version", "config", "1.0.0"),
                        varDef("dataSourcePath", "Target schema data source", "config", ""),
                        varDef("sql", "DDL/DML SQL", "config", ""),
                        varDef("checksum", "Applied checksum", "runtime", ""),
                        varDef("appliedAt", "Applied at ISO instant", "runtime", "")
                ),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicModels.parameters(),
                Instant.now(),
                Instant.now()
        );
    }

    private static ModelVariableDefinition varDef(String name, String description, String group, String defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                group,
                STRING_VALUE_SCHEMA,
                true,
                true, DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition intDef(String name, String description, int defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                "config",
                INTEGER_VALUE_SCHEMA,
                true,
                true, DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }

    private static ModelVariableDefinition boolDef(String name, String description, boolean defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                "config",
                BOOLEAN_VALUE_SCHEMA,
                true,
                true, DataRecord.single(BOOLEAN_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }
}
