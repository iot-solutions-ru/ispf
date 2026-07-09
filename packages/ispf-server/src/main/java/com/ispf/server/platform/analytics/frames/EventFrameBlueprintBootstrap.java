package com.ispf.server.platform.analytics.frames;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EventFrameBlueprintBootstrap {

    public static final String EVENT_FRAMES_ROOT = "root.platform.event-frames";
    public static final String EVENT_FRAME_MODEL = "event-frame-v1";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final ObjectManager objectManager;

    public EventFrameBlueprintBootstrap(
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            ObjectManager objectManager
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.objectManager = objectManager;
    }

    public void ensureEventFrameModels() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, EVENT_FRAMES_ROOT, ObjectType.EVENT_FRAMES, null);
        blueprintRegistry.findByName(EVENT_FRAME_MODEL).ifPresentOrElse(
                existing -> {
                },
                () -> blueprintEngine.createBlueprint(buildEventFrameModel())
        );
    }

    private static BlueprintDefinition buildEventFrameModel() {
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                EVENT_FRAME_MODEL,
                "Analytics event frame — shift, batch, downtime, or custom time window (BL-208)",
                BlueprintType.INSTANCE,
                ObjectType.EVENT_FRAME,
                "",
                List.of(
                        varDef("frameType", "Frame type (shift, batch, downtime, custom)", "info", "custom"),
                        varDef("scopePath", "Scoped asset or line path", "config", ""),
                        varDef("sourcePath", "Optional source object path", "config", ""),
                        varDef("sourceKey", "Optional external key (shiftId, batch path)", "config", ""),
                        varDef("label", "Human-readable label", "info", ""),
                        varDef("startedAt", "Frame start (ISO-8601)", "runtime", ""),
                        varDef("endedAt", "Frame end (ISO-8601, empty when active)", "runtime", ""),
                        intDef("downtimeMinutes", "Registered downtime minutes", "runtime", 0)
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

    private static BlueprintVariableDefinition intDef(String name, String description, String group, int defaultValue) {
        return BlueprintVariableDefinition.of(
                name,
                description,
                group,
                INTEGER_VALUE,
                true,
                true,
                DataRecord.single(INTEGER_VALUE, Map.of("value", defaultValue))
        );
    }

    static String readString(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("");
    }
}
