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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brick Schema overlay mixin ({@value #BRICK_METADATA_MODEL}) — BL-60.
 */
@Component
public class BrickBlueprintBootstrap {

    public static final String BRICK_METADATA_MODEL = "brick-metadata-v1";

    public static final String DEMO_BRICK_CLASS = "https://brickschema.org/schema/Brick#Sensor";

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;

    public BrickBlueprintBootstrap(BlueprintEngine blueprintEngine, BlueprintRegistry blueprintRegistry) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
    }

    public void ensureBrickModel() {
        if (blueprintRegistry.findByName(BRICK_METADATA_MODEL).isEmpty()) {
            blueprintEngine.createBlueprint(buildBrickMetadataModel());
        }
    }

    static BlueprintDefinition buildBrickMetadataModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                BRICK_METADATA_MODEL,
                "Brick Schema class overlay — optional brickClass URI on devices (object tree remains source of truth)",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "",
                List.of(
                        BlueprintVariableDefinition.of(
                                "brickClass",
                                "Brick class URI or CURIE, e.g. https://brickschema.org/schema/Brick#Sensor",
                                "brick",
                                STRING_VALUE_SCHEMA,
                                true,
                                true,
                                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", ""))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
