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
 * Haystack semantic overlay mixin ({@value #HAYSTACK_METADATA_MODEL}) — BL-57.
 */
@Component
public class HaystackModelBootstrap {

    public static final String HAYSTACK_METADATA_MODEL = "haystack-metadata-v1";

    public static final String DEMO_DEVICE_PATH = "root.platform.devices.lab-userA-01";
    public static final String DEMO_HAYSTACK_REF = "@demo.lab.equip1";
    public static final String DEMO_HAYSTACK_TAGS = "[\"equip\",\"lab\",\"site\"]";
    public static final String DEMO_POINT_MAPPINGS = LabModelBootstrap.LAB_POINT_MAPPINGS;

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public HaystackModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureHaystackModel() {
        if (modelRegistry.findByName(HAYSTACK_METADATA_MODEL).isEmpty()) {
            modelEngine.createModel(buildHaystackMetadataModel());
        }
    }

    static ModelDefinition buildHaystackMetadataModel() {
        return new ModelDefinition(
                UUID.randomUUID().toString(),
                HAYSTACK_METADATA_MODEL,
                "Haystack tag overlay — optional metadata on devices (object tree remains source of truth)",
                ModelType.RELATIVE,
                ObjectType.DEVICE,
                "",
                List.of(
                        stringVar(
                                "haystackTags",
                                "Haystack marker tags as JSON string array, e.g. [\"equip\",\"point\",\"temp\"]",
                                "[]"
                        ),
                        stringVar(
                                "haystackRef",
                                "Optional external Haystack ref (does not replace ISPF path)",
                                ""
                        ),
                        stringVar(
                                "haystackKind",
                                "Primary Haystack kind: equip, point, site, …",
                                ""
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

    private static ModelVariableDefinition stringVar(String name, String description, String defaultValue) {
        return ModelVariableDefinition.of(
                name,
                description,
                "haystack",
                STRING_VALUE_SCHEMA,
                true,
                true,
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", defaultValue))
        );
    }
}
