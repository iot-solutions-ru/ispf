package com.ispf.server.application.bff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default field labels for wire profile {@code anima-operator-v1} (REQ-PF-06).
 * Schema field {@code description} overrides these defaults.
 */
final class AnimaOperatorWireProfile {

    static final String ID = "anima-operator-v1";

    private static final Map<String, String> DEFAULT_FIELD_LABELS = Map.ofEntries(
            Map.entry("item_code", "Код позиции"),
            Map.entry("order_number", "Номер заказа"),
            Map.entry("status", "Статус"),
            Map.entry("code", "Код"),
            Map.entry("state", "Состояние"),
            Map.entry("location_code", "Ячейка"),
            Map.entry("created_at", "Создано"),
            Map.entry("updated_at", "Обновлено")
    );

    private AnimaOperatorWireProfile() {
    }

    static Map<String, String> defaultFieldLabels() {
        return DEFAULT_FIELD_LABELS;
    }

    static String resolveLabel(String fieldName, String schemaDescription, Map<String, String> profileDefaults) {
        if (schemaDescription != null && !schemaDescription.isBlank()) {
            return schemaDescription.trim();
        }
        String fromProfile = profileDefaults.get(fieldName);
        if (fromProfile != null && !fromProfile.isBlank()) {
            return fromProfile;
        }
        return fieldName;
    }

    static Map<String, String> labelsFromSchemaFields(
            Iterable<com.ispf.core.model.FieldDefinition> fields,
            Map<String, String> profileDefaults
    ) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (com.ispf.core.model.FieldDefinition field : fields) {
            labels.put(field.name(), resolveLabel(field.name(), field.description(), profileDefaults));
        }
        return labels;
    }
}
