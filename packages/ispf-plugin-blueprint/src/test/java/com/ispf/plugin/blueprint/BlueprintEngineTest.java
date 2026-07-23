package com.ispf.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.expression.ExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintEngineTest {

    private ObjectTree objectTree;
    private BlueprintEngine engine;

    @BeforeEach
    void setUp() {
        objectTree = new ObjectTree();
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform",
                ObjectType.CUSTOM,
                "Platform",
                null,
                null
        ));
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices",
                ObjectType.CUSTOM,
                "Devices",
                null,
                null
        ));

        engine = new BlueprintEngine(
                new BlueprintRegistry(),
                objectTree,
                new ExpressionEngine()
        );
    }

    @Test
    void createsAndAppliesRelativeModel() {
        DataSchema temperatureSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();

        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "mqtt-sensor-v1",
                "MQTT temperature sensor",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(BlueprintVariableDefinition.withHistory(
                        "temperature",
                        "Current temperature",
                        "telemetry",
                        temperatureSchema,
                        true,
                        true, DataRecord.single(temperatureSchema, Map.of("value", 0.0, "unit", "C"))
                )),
                List.of(new EventDescriptor(
                        "thresholdExceeded",
                        "Threshold exceeded",
                        temperatureSchema,
                        EventLevel.WARNING
                )),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(model);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.sensor-1",
                ObjectType.DEVICE,
                "sensor-1",
                null,
                null
        );
        objectTree.register(device);

        BlueprintApplyResult result = engine.applyBlueprint(model.id(), device.path());

        assertThat(result.attachment().blueprintName()).isEqualTo("mqtt-sensor-v1");
        assertThat(device.getVariable("temperature")).isPresent();
        assertThat(device.events()).containsKey("thresholdExceeded");
    }

    @Test
    void applyMixinModelsSkipsBlankApplicabilityExpression() {
        DataSchema temperatureSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .build();

        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "auto-skip-sensor",
                "Sensor without applicability CEL",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(BlueprintVariableDefinition.of(
                        "temperature",
                        "Current temperature",
                        "telemetry",
                        temperatureSchema,
                        true,
                        true, DataRecord.single(temperatureSchema, Map.of("value", 0.0))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(model);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.auto-skip",
                ObjectType.DEVICE,
                "auto-skip",
                null,
                null
        );
        objectTree.register(device);

        List<BlueprintApplyResult> applied = engine.applyMixinBlueprints(device.path());

        assertThat(applied).isEmpty();
        assertThat(device.getVariable("temperature")).isEmpty();
    }

    @Test
    void applyMixinModelsUsesApplicabilityExpression() {
        DataSchema flagSchema = DataSchema.builder("flag")
                .field("value", FieldType.BOOLEAN)
                .build();

        BlueprintDefinition matching = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "cel-match",
                "Applies when flag is true",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "self.flag.value == true",
                List.of(BlueprintVariableDefinition.of(
                        "matched",
                        "Matched marker",
                        "meta",
                        flagSchema,
                        true,
                        false,
                        DataRecord.single(flagSchema, Map.of("value", true))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        BlueprintDefinition nonMatching = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "cel-no-match",
                "Applies when flag is false",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "self.flag.value == false",
                List.of(BlueprintVariableDefinition.of(
                        "nonMatched",
                        "Non-matched marker",
                        "meta",
                        flagSchema,
                        true,
                        false,
                        DataRecord.single(flagSchema, Map.of("value", false))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(matching);
        engine.createBlueprint(nonMatching);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.cel-device",
                ObjectType.DEVICE,
                "cel-device",
                null,
                null
        );
        objectTree.register(device);
        device.addVariable(new Variable(
                "flag",
                flagSchema,
                true,
                false,
                DataRecord.single(flagSchema, Map.of("value", true))
        ));

        List<BlueprintApplyResult> applied = engine.applyMixinBlueprints(device.path());

        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst().attachment().blueprintName()).isEqualTo("cel-match");
        assertThat(device.getVariable("matched")).isPresent();
        assertThat(device.getVariable("nonMatched")).isEmpty();
    }

    @Test
    void intrinsicModelsSkipCatalogAndappliedBlueprintIds() {
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                BlueprintCatalogRoots.MIXIN,
                ObjectType.BLUEPRINT,
                "Mixin Blueprints",
                null,
                null
        ));
        objectTree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.data-sources",
                ObjectType.DATA_SOURCES,
                "Data Sources",
                null,
                null
        ));

        BlueprintDefinition intrinsic = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "data-source-v1",
                "Data source schema",
                BlueprintType.MIXIN,
                ObjectType.DATA_SOURCE,
                "",
                List.of(BlueprintVariableDefinition.of(
                        "schemaName",
                        "Schema",
                        "config",
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        true,
                        true,
                        DataRecord.single(
                                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                                Map.of("value", "public")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                SystemIntrinsicBlueprints.parameters(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(intrinsic);

        assertThat(objectTree.findByPath("root.platform.mixin-blueprints.data-source-v1")).isEmpty();

        PlatformObject dataSource = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.data-sources.app1",
                ObjectType.DATA_SOURCE,
                "app1",
                null,
                null
        );
        objectTree.register(dataSource);

        BlueprintApplyResult result = engine.applyIntrinsicStructure(intrinsic, dataSource.path());

        assertThat(result.attachment()).isNull();
        assertThat(dataSource.getVariable("schemaName")).isPresent();
        assertThat(dataSource.appliedBlueprintIds()).isEmpty();
    }

    @Test
    void instantiatesInstanceModel() {
        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "pump-controller",
                "Pump controller",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(model);

        BlueprintApplyResult result = engine.instantiateBlueprint(
                model.id(),
                "root.platform.devices",
                "pump-01",
                Map.of()
        );
        PlatformObject instance = objectTree.require("root.platform.devices.pump-01");

        assertThat(instance.path()).isEqualTo("root.platform.devices.pump-01");
        assertThat(instance.templateId()).contains(model.id());
        assertThat(result.attachment().blueprintId()).isEqualTo(model.id());
    }

    @Test
    void rejectsRelativeInstantiation() {
        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "relative-only",
                "",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(model);

        assertThatThrownBy(() -> engine.instantiateBlueprint(
                model.id(),
                "root.platform.devices",
                "x",
                Map.of()
        )).isInstanceOf(BlueprintException.class);
    }

    @Test
    void bindingRulesStoredOnModel() {
        DataSchema alarmSchema = DataSchema.builder("alarmActive")
                .field("value", FieldType.BOOLEAN)
                .build();
        DataSchema thresholdSchema = DataSchema.builder("threshold")
                .field("value", FieldType.DOUBLE)
                .build();

        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "sensor",
                "",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "alarmActive",
                                "",
                                "status",
                                alarmSchema,
                                true,
                                false,
                                DataRecord.single(alarmSchema, Map.of("value", false))
                        ),
                        BlueprintVariableDefinition.of(
                                "temperaturePercent",
                                "",
                                "telemetry",
                                thresholdSchema,
                                true,
                                false,
                                DataRecord.single(thresholdSchema, Map.of("value", 0.0))
                        )
                ),
                List.of(),
                List.of(),
                List.of(
                        BlueprintBindingRule.of("alarm-active", "alarmActive", "self.temperature.value > self.threshold.value"),
                        BlueprintBindingRule.of("temperature-percent", "temperaturePercent", "scale(temperature, 0, 100, 0, 1)")
                ),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        assertThat(model.bindingRules()).hasSize(2);
        assertThat(model.bindingRules().getFirst().targetVariable()).isEqualTo("alarmActive");
    }

    @Test
    void appliesModelInheritanceChain() {
        DataSchema baseSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .build();
        BlueprintDefinition base = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "sensor-base-v1",
                "Base sensor",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(BlueprintVariableDefinition.of(
                        "temperature",
                        "Temperature",
                        "telemetry",
                        baseSchema,
                        true,
                        true, DataRecord.single(baseSchema, Map.of("value", 0.0))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        BlueprintDefinition extension = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                "sensor-vendor-v1",
                "Vendor extension",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(BlueprintVariableDefinition.of(
                        "vendorId",
                        "Vendor id",
                        "meta",
                        DataSchema.builder("vendorId").field("value", FieldType.STRING).build(),
                        true,
                        true, DataRecord.single(
                                DataSchema.builder("vendorId").field("value", FieldType.STRING).build(),
                                Map.of("value", "ACME")
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of("extendsBlueprintId", base.id()),
                Instant.now(),
                Instant.now()
        );
        engine.createBlueprint(base);
        engine.createBlueprint(extension);

        PlatformObject device = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.vendor-sensor",
                ObjectType.DEVICE,
                "vendor-sensor",
                null,
                null
        );
        objectTree.register(device);

        engine.applyBlueprint(extension.id(), device.path());

        assertThat(device.getVariable("temperature")).isPresent();
        assertThat(device.getVariable("vendorId")).isPresent();
    }
}
