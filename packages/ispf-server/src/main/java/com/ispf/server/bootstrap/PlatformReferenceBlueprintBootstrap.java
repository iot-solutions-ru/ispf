package com.ispf.server.bootstrap;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Platform INSTANCE models documented in {@code docs/en/blueprints.md} / {@code docs/en/drivers.md}.
 * Always registered at startup — independent of demo object fixtures.
 */
@Component
public class PlatformReferenceBlueprintBootstrap {

    public static final String SNMP_AGENT_MODEL = "snmp-agent-v1";
    public static final String MQTT_GATEWAY_SENSOR_MODEL = "mqtt-gateway-sensor-v1";

    /** Stable ids so catalog nodes survive restarts and match on every cluster replica. */
    public static final String SNMP_AGENT_MODEL_ID = "16516516-5165-1651-6516-516516516517";
    public static final String MQTT_GATEWAY_SENSOR_MODEL_ID = "16516516-5165-1651-6516-516516516516";

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintPersistenceService blueprintPersistence;
    private final ObjectManager objectManager;

    public PlatformReferenceBlueprintBootstrap(
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

    public void ensureReferenceModels() {
        ensureModel(FixtureBlueprintDefinitions.buildSnmpAgentModel());
        ensureModel(FixtureBlueprintDefinitions.buildMqttGatewaySensorInstanceModel());
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
}
