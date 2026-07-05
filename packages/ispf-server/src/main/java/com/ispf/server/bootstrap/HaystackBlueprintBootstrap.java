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
 * Haystack semantic overlay mixin ({@value #HAYSTACK_METADATA_MODEL}) — BL-57.
 */
@Component
public class HaystackBlueprintBootstrap {

    public static final String HAYSTACK_METADATA_MODEL = "haystack-metadata-v1";

    public static final String DEMO_DEVICE_PATH = "root.platform.devices.lab-userA-01";
    public static final String DEMO_HAYSTACK_REF = "@demo.lab.equip1";
    public static final String DEMO_HAYSTACK_TAGS = "[\"equip\",\"lab\",\"site\"]";
    public static final String DEMO_POINT_MAPPINGS = LabBlueprintBootstrap.LAB_POINT_MAPPINGS;

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final BlueprintEngine BlueprintEngine;
    private final BlueprintRegistry BlueprintRegistry;

    public HaystackBlueprintBootstrap(BlueprintEngine BlueprintEngine, BlueprintRegistry BlueprintRegistry) {
        this.BlueprintEngine = BlueprintEngine;
        this.BlueprintRegistry = BlueprintRegistry;
    }

    public void ensureHaystackModel() {
        if (BlueprintRegistry.findByName(HAYSTACK_METADATA_MODEL).isEmpty()) {
            BlueprintEngine.createBlueprint(buildHaystackMetadataModel());
        }
    }

    static BlueprintDefinition buildHaystackMetadataModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                HAYSTACK_METADATA_MODEL,
                "Haystack tag overlay — optional metadata on devices (object tree remains source of truth)",
                BlueprintType.RELATIVE,
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

    private static BlueprintVariableDefinition stringVar(String name, String description, String defaultValue) {
        return BlueprintVariableDefinition.of(
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
