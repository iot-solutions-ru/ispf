package com.ispf.server.application.reference.mes;

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
 * ISA-88 batch and MES instance-type blueprints (BL-168).
 */
@Component
public class MesBlueprintBootstrap {

    public static final String BATCH_MODEL = "batch-v1";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public MesBlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensureMesModels() {
        ensureModel(buildBatchModel());
    }

    private void ensureModel(BlueprintDefinition definition) {
        blueprintRegistry.findByName(definition.name()).ifPresentOrElse(
                existing -> {
                    if (existing.variables().size() < definition.variables().size()) {
                        blueprintEngine.updateBlueprint(new BlueprintDefinition(
                                existing.id(),
                                definition.name(),
                                definition.description(),
                                definition.type(),
                                definition.targetObjectType(),
                                definition.suitabilityExpression(),
                                definition.variables(),
                                definition.events(),
                                definition.functions(),
                                definition.bindingRules(),
                                definition.parameters(),
                                existing.createdAt(),
                                Instant.now()
                        ));
                    }
                },
                () -> blueprintEngine.createBlueprint(definition)
        );
    }

    private static BlueprintDefinition buildBatchModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                BATCH_MODEL,
                "ISA-88 batch instance — recipe, phase, batch identity (BL-168)",
                BlueprintType.INSTANCE,
                ObjectType.LOT,
                "",
                List.of(
                        varDef("batchId", "Batch identifier", "info", ""),
                        varDef("recipe", "Recipe id or name", "config", ""),
                        varDef("phase", "Current ISA-88 phase (charge, react, discharge, …)", "runtime", "idle")
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
}
