package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registers analytics template intrinsic schema and derived-tag RELATIVE blueprints (BL-160).
 */
@Component
public class AnalyticsBlueprintBootstrap {

    public static final String TEMPLATE_INTRINSIC = "analytics-template-v1";
    public static final String ROLLING_AVG_MODEL = "rolling-avg-v1";
    public static final String RATE_OF_CHANGE_MODEL = "rate-of-change-v1";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public AnalyticsBlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensureAnalyticsModels() {
        ensureModel(TEMPLATE_INTRINSIC, buildTemplateIntrinsic());
        ensureModel(ROLLING_AVG_MODEL, buildRollingAvgModel());
        ensureModel(RATE_OF_CHANGE_MODEL, buildRateOfChangeModel());
    }

    private void ensureModel(String name, BlueprintDefinition definition) {
        BlueprintDefinition target = TEMPLATE_INTRINSIC.equals(name)
                ? definition.withSystemIntrinsicFlag()
                : definition;
        blueprintRegistry.findByName(name).ifPresentOrElse(
                existing -> {
                    if (TEMPLATE_INTRINSIC.equals(name) && !existing.systemIntrinsic()) {
                        blueprintEngine.updateBlueprint(existing.withSystemIntrinsicFlag());
                    }
                },
                () -> blueprintEngine.createBlueprint(target)
        );
    }

    private static BlueprintDefinition buildTemplateIntrinsic() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                TEMPLATE_INTRINSIC,
                "Asset analytics derived-tag template metadata (BL-160)",
                BlueprintType.RELATIVE,
                ObjectType.ANALYTICS_TEMPLATE,
                "",
                List.of(
                        varDef("templateId", "Stable template id", "info", ""),
                        varDef("helper", "Analytics helper (rollingAvg, rateOfChange)", "config", ""),
                        varDef("sourcePath", "Source object path", "config", ""),
                        varDef("sourceVariable", "Source variable name", "config", ""),
                        varDef("sourceField", "Source schema field", "config", "value"),
                        varDef("windowBucket", "Historian bucket/window (1m, 5m, 1h, …)", "config", "5m"),
                        varDef("blueprintName", "Optional linked RELATIVE blueprint", "config", ""),
                        boolDef("enabled", "Template enabled", "config", true)
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition buildRollingAvgModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                ROLLING_AVG_MODEL,
                "Rolling average derived tag — binds historian aggregate avg over windowBucket",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        varDef("derivedValue", "Computed rolling average", "runtime", "0"),
                        varDef("sourcePath", "Source object path", "config", ""),
                        varDef("sourceVariable", "Source variable name", "config", ""),
                        varDef("sourceField", "Source schema field", "config", "value"),
                        varDef("windowBucket", "Historian aggregate bucket", "config", "5m")
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition buildRateOfChangeModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                RATE_OF_CHANGE_MODEL,
                "Rate-of-change derived tag — delta per window using historian buckets",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        varDef("derivedValue", "Computed rate of change", "runtime", "0"),
                        varDef("sourcePath", "Source object path", "config", ""),
                        varDef("sourceVariable", "Source variable name", "config", ""),
                        varDef("sourceField", "Source schema field", "config", "value"),
                        varDef("windowBucket", "Historian bucket for delta", "config", "1h")
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintVariableDefinition varDef(String name, String description, String group, String defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                group,
                STRING_VALUE,
                true,
                true,
                DataRecord.single(STRING_VALUE, Map.of("value", defaultValue))
        );
    }

    private static BlueprintVariableDefinition boolDef(String name, String description, String group, boolean defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                group,
                BOOLEAN_VALUE,
                true,
                true,
                DataRecord.single(BOOLEAN_VALUE, Map.of("value", defaultValue))
        );
    }
}
