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
    public static final String ANALYTICS_TAG_MODEL = "analytics-tag-v1";
    public static final String ROLLING_AVG_MODEL = "rolling-avg-v1";
    public static final String RATE_OF_CHANGE_MODEL = "rate-of-change-v1";
    public static final String OEE_MODEL = "oee-v1";

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
        ensureModel(ANALYTICS_TAG_MODEL, buildAnalyticsTagModel());
        ensureModel(ROLLING_AVG_MODEL, buildRollingAvgModel());
        ensureModel(RATE_OF_CHANGE_MODEL, buildRateOfChangeModel());
        ensureModel(OEE_MODEL, buildOeeModel());
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
                        varDef("helper", "Analytics helper (rollingAvg, rateOfChange, totalizer, min, max, last, oee)", "config", ""),
                        varDef("sourcePath", "Source object path", "config", ""),
                        varDef("sourceVariable", "Source variable name", "config", ""),
                        varDef("sourceField", "Source schema field", "config", "value"),
                        varDef("windowBucket", "Historian bucket/window (1m, 5m, 1h, …)", "config", "5m"),
                        varDef("rollupBuckets", "Materialized rollup windows (comma-separated)", "config", "5m,1h,8h"),
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

    private static BlueprintDefinition buildAnalyticsTagModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                ANALYTICS_TAG_MODEL,
                "Deployed analytics tag metadata — expression, lineage, quality (BL-209)",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        varDef("analyticsExpression", "Human-readable expression", "analytics", ""),
                        varDef("analyticsHelper", "Engine helper name", "analytics", ""),
                        varDef("analyticsLineageJson", "Comma-separated upstream tag paths", "analytics", ""),
                        varDef("analyticsQuality", "Quality: ok, uncertain, error, disabled", "analytics", "ok"),
                        varDef("analyticsLastEvalAt", "Last evaluation timestamp (ISO-8601)", "analytics", ""),
                        varDef("analyticsLastEvalStatus", "Last eval status: ok, error, skipped", "analytics", ""),
                        varDef("analyticsHaystackTags", "Haystack tags on output point (comma-separated)", "analytics", "point,cur,his"),
                        boolDef("analyticsTagEnabled", "Tag enabled for engine evaluation", "analytics", true)
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
                        varDef("windowBucket", "Historian aggregate bucket", "config", "5m"),
                        varDef("rollupBuckets", "Materialized rollup windows for source tag", "config", "5m,1h,8h")
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
                        varDef("windowBucket", "Historian bucket for delta", "config", "1h"),
                        varDef("rollupBuckets", "Materialized rollup windows for source tag", "config", "5m,1h,8h")
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BlueprintDefinition buildOeeModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                OEE_MODEL,
                "OEE composite KPI — Availability × Performance × Quality over shift window",
                BlueprintType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        varDef("oeePct", "Composite OEE percent (A×P×Q×100)", "runtime", "0"),
                        varDef("availabilityPct", "Availability percent", "runtime", "0"),
                        varDef("performancePct", "Performance percent", "runtime", "0"),
                        varDef("qualityPct", "Quality percent", "runtime", "0"),
                        varDef("sourcePath", "Line or asset object path", "config", ""),
                        varDef("availabilityVariable", "Planned/runtime availability source variable", "config", ""),
                        varDef("performanceVariable", "Throughput or cycle-time source variable", "config", ""),
                        varDef("qualityVariable", "Good/total quality source variable", "config", ""),
                        varDef("sourceField", "Source schema field", "config", "value"),
                        varDef("windowBucket", "Shift or rollup bucket (1h, 8h, 1d)", "config", "8h"),
                        varDef("eventFrameScope", "Scope path for active shift event frame (BL-208)", "config", "")
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
