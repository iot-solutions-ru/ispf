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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brick Schema overlay mixin ({@value #BRICK_METADATA_MODEL}) — BL-60.
 */
@Component
public class BrickModelBootstrap {

    public static final String BRICK_METADATA_MODEL = "brick-metadata-v1";

    public static final String DEMO_BRICK_CLASS = "https://brickschema.org/schema/Brick#Sensor";

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public BrickModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureBrickModel() {
        if (modelRegistry.findByName(BRICK_METADATA_MODEL).isEmpty()) {
            modelEngine.createModel(buildBrickMetadataModel());
        }
    }

    static ModelDefinition buildBrickMetadataModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                BRICK_METADATA_MODEL,
                "Brick Schema class overlay — optional brickClass URI on devices (object tree remains source of truth)",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        ModelVariableDefinition.of(
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
