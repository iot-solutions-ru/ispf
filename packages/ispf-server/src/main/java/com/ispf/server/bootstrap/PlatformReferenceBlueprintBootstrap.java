package com.ispf.server.bootstrap;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import org.springframework.stereotype.Component;

/**
 * Platform INSTANCE models documented in {@code docs/BLUEPRINTS.md} / {@code docs/DRIVERS.md}.
 * Always registered at startup — independent of demo object fixtures.
 */
@Component
public class PlatformReferenceBlueprintBootstrap {

    public static final String SNMP_AGENT_MODEL = "snmp-agent-v1";
    public static final String MQTT_GATEWAY_SENSOR_MODEL = "mqtt-gateway-sensor-v1";

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public PlatformReferenceBlueprintBootstrap(
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensureReferenceModels() {
        ensureSnmpAgentModel();
        ensureMqttGatewaySensorInstanceModel();
    }

    private void ensureSnmpAgentModel() {
        if (blueprintRegistry.findByName(SNMP_AGENT_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildSnmpAgentModel());
        }
    }

    /** Gateway child sensor INSTANCE type — required by MQTT gateway dispatch on any environment. */
    private void ensureMqttGatewaySensorInstanceModel() {
        if (blueprintRegistry.findByName(MQTT_GATEWAY_SENSOR_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(FixtureBlueprintDefinitions.buildMqttGatewaySensorInstanceModel());
            return;
        }
        BlueprintDefinition existing = blueprintRegistry.requireByName(MQTT_GATEWAY_SENSOR_MODEL);
        if (existing.variables().size() >= 5) {
            return;
        }
        BlueprintDefinition fresh = FixtureBlueprintDefinitions.buildMqttGatewaySensorInstanceModel();
        blueprintEngine.updateBlueprint(new BlueprintDefinition(
                existing.id(),
                fresh.name(),
                fresh.description(),
                fresh.type(),
                fresh.targetObjectType(),
                fresh.suitabilityExpression(),
                fresh.variables(),
                fresh.events(),
                fresh.functions(),
                fresh.bindings(),
                fresh.parameters(),
                existing.createdAt(),
                java.time.Instant.now()
        ));
    }
}
