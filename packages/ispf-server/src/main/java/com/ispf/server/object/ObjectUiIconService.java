package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class ObjectUiIconService {

    public static final String UI_ICON_VARIABLE = "uiIcon";

    private static final DataSchema UI_ICON_SCHEMA = DataSchema.builder(UI_ICON_VARIABLE)
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;

    public ObjectUiIconService(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public Optional<String> readIconId(PlatformObject node) {
        return node.getVariable(UI_ICON_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> {
                    if (record.rows().isEmpty()) {
                        return "";
                    }
                    Object raw = record.rows().getFirst().get("value");
                    return raw != null ? String.valueOf(raw).trim() : "";
                })
                .filter(value -> !value.isBlank());
    }

    @Transactional
    public void setIconId(String path, String iconId) {
        if (iconId == null || iconId.isBlank()) {
            clearIconId(path);
            return;
        }
        String normalized = iconId.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("iconId too long (max 64)");
        }
        DataRecord record = DataRecord.single(UI_ICON_SCHEMA, Map.of("value", normalized));
        PlatformObject node = objectManager.require(path);
        if (node.getVariable(UI_ICON_VARIABLE).isEmpty()) {
            node.addVariable(new Variable(UI_ICON_VARIABLE, UI_ICON_SCHEMA, true, true, record));
            objectManager.persistNodeTree(path);
            return;
        }
        objectManager.setVariableValue(path, UI_ICON_VARIABLE, record);
    }

    @Transactional
    public void clearIconId(String path) {
        objectManager.deleteVariable(path, UI_ICON_VARIABLE);
    }
}
