package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INSTANCE models for the anonymized tank-farm SCADA demo.
 */
@Component
public class TankFarmBlueprintBootstrap {

    public static final String TANK_MODEL = "tank-farm-tank-v1";
    public static final String MANIFOLD_HUB_MODEL = "tank-farm-hub-v1";

    public static String tankDriverConfig(int tankIndex, double initialLevelMm, double rateBiasMmPerHour) {
        return String.format(
                "{\"profile\":\"tank-farm-tank\",\"tankIndex\":\"%d\",\"initialLevelMm\":\"%.0f\",\"rateBiasMmPerHour\":\"%.0f\",\"maxLevelMm\":\"10000\"}",
                tankIndex,
                initialLevelMm,
                rateBiasMmPerHour
        );
    }

    public static final String MANIFOLD_HUB_DRIVER_CONFIG = "{\"profile\":\"tank-farm-hub\"}";

    private static final DataSchema MEAS = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema STR = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine BlueprintEngine;
    private final BlueprintRegistry BlueprintRegistry;

    public TankFarmBlueprintBootstrap(BlueprintEngine BlueprintEngine, BlueprintRegistry BlueprintRegistry) {
        this.BlueprintEngine = BlueprintEngine;
        this.BlueprintRegistry = BlueprintRegistry;
    }

    public void ensureTankFarmModels() {
        ensure(buildTankModel());
        ensure(buildHubModel());
    }

    private void ensure(BlueprintDefinition desired) {
        var existing = BlueprintRegistry.findByName(desired.name());
        if (existing.isEmpty()) {
            BlueprintEngine.createBlueprint(desired);
            return;
        }
        BlueprintDefinition current = existing.get();
        if (current.variables().size() >= desired.variables().size()) {
            return;
        }
        BlueprintEngine.updateBlueprint(new BlueprintDefinition(
                current.id(),
                desired.name(),
                desired.description(),
                desired.type(),
                desired.targetObjectType(),
                desired.suitabilityExpression(),
                desired.variables(),
                desired.events(),
                desired.functions(),
                desired.bindingRules(),
                desired.parameters(),
                current.createdAt(),
                Instant.now()
        ));
    }

    private BlueprintDefinition buildTankModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("fillLevelMm", "Fill level", "telemetry", "mm", 5000));
        vars.add(meas("rateMmPerHour", "Level change rate", "telemetry", "mm/h", 0));
        vars.add(meas("maxLevelMm", "Max level", "config", "mm", 10000));
        vars.add(boolVar("valveOpen", "Outlet valve open", "status", false, false));
        vars.addAll(driverVars("{\"profile\":\"tank-farm-tank\"}"));
        return model(TANK_MODEL, "Storage tank (demo)", ObjectType.DEVICE, vars, List.of());
    }

    private BlueprintDefinition buildHubModel() {
        List<BlueprintVariableDefinition> vars = new ArrayList<>();
        vars.add(meas("linePressureMpa", "Line pressure", "telemetry", "MPa", 0.82));
        vars.add(meas("lineFlowM3h", "Line flow", "telemetry", "m³/h", 1240));
        vars.add(meas("lineTempC", "Line temperature", "telemetry", "°C", 12));
        vars.add(meas("deltaPressureMpa", "Differential pressure", "telemetry", "MPa", 0.04));
        vars.add(meas("totalVolumeM3", "Total stored volume", "telemetry", "m³", 81000));
        vars.addAll(driverVars(MANIFOLD_HUB_DRIVER_CONFIG));
        return model(MANIFOLD_HUB_MODEL, "Pipeline manifold hub (demo)", ObjectType.CUSTOM, vars, List.of());
    }

    private static List<BlueprintVariableDefinition> driverVars(String configJson) {
        return List.of(
                BlueprintVariableDefinition.of("driverId", "Driver id", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", "virtual"))),
                BlueprintVariableDefinition.of("driverConfigJson", "Driver config", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", configJson))),
                BlueprintVariableDefinition.of("driverPointMappingsJson", "Point mappings", "driver", STR, true, true,
                        DataRecord.single(STR, Map.of("value", "{\"status\":\"sim\"}")))
        );
    }

    private static BlueprintVariableDefinition meas(
            String name, String desc, String group, String unit, double def
    ) {
        return BlueprintVariableDefinition.withHistory(
                name, desc, group, MEAS, true, false,
                DataRecord.single(MEAS, Map.of("value", def, "unit", unit))
        );
    }

    private static BlueprintVariableDefinition boolVar(
            String name, String desc, String group, boolean def, boolean writable
    ) {
        return BlueprintVariableDefinition.of(name, desc, group, BOOL, true, writable,
                DataRecord.single(BOOL, Map.of("value", def)));
    }

    private static BlueprintDefinition model(
            String name,
            String description,
            ObjectType type,
            List<BlueprintVariableDefinition> variables,
            List<BlueprintBindingRule> bindings
    ) {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                name,
                description,
                BlueprintType.INSTANCE,
                type,
                "",
                variables,
                List.of(),
                List.of(),
                bindings,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
