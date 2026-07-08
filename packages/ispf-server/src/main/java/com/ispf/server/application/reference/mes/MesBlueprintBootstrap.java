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
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ISA-88 batch and MES instance-type blueprints (BL-168).
 */
@Component
public class MesBlueprintBootstrap {

    public static final String BATCH_MODEL = "batch-v1";
    public static final String WORK_ORDER_MODEL = "work-order-v1";

    /** Stable id so catalog nodes survive restarts when persisted as builtin. */
    private static final String BATCH_MODEL_ID = "16816816-8168-1681-6816-816816816816";
    private static final String WORK_ORDER_MODEL_ID = "16616616-6166-1661-6616-616616616616";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintPersistenceService blueprintPersistence;
    private final ObjectManager objectManager;

    public MesBlueprintBootstrap(
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            BlueprintPersistenceService blueprintPersistence,
            ObjectManager objectManager
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintPersistence = blueprintPersistence;
        this.objectManager = objectManager;
    }

    public void ensureMesModels() {
        ensureModel(buildBatchModel());
        ensureModel(buildWorkOrderModel());
    }

    private void ensureModel(BlueprintDefinition definition) {
        blueprintRegistry.findByName(definition.name()).ifPresentOrElse(
                existing -> {
                    if (existing.variables().size() < definition.variables().size()) {
                        BlueprintDefinition updated = blueprintEngine.updateBlueprint(new BlueprintDefinition(
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
                        persistBuiltin(updated);
                    } else {
                        persistBuiltin(existing);
                    }
                },
                () -> persistBuiltin(blueprintEngine.createBlueprint(definition))
        );
    }

    private void persistBuiltin(BlueprintDefinition model) {
        blueprintPersistence.persist(model, true);
        objectManager.persistNodeTree(model.catalogObjectPath());
    }

    private static BlueprintDefinition buildBatchModel() {
        return new BlueprintDefinition(
                BATCH_MODEL_ID,
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
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static BlueprintDefinition buildWorkOrderModel() {
        return new BlueprintDefinition(
                WORK_ORDER_MODEL_ID,
                WORK_ORDER_MODEL,
                "Manufacturing work order instance — dispatch, line, status (BL-164 / BL-166)",
                BlueprintType.INSTANCE,
                ObjectType.WORK_ORDER,
                "",
                List.of(
                        varDef("orderNumber", "Work order number", "info", ""),
                        varDef("lineCode", "ISA-95 line code", "routing", ""),
                        varDef("status", "Lifecycle status (planned, dispatched, in-progress, complete)", "runtime", "planned"),
                        varDef("priority", "Priority label (low, normal, high, urgent)", "config", "normal")
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
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
