package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Workflow variables that are stored as JSON text must be readable before they replace the previous value.
 */
public final class WorkflowStoredJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowStoredJson() {
    }

    public static void rejectUnreadable(PlatformObject node, String name, DataRecord value) {
        if (node.type() != ObjectType.WORKFLOW || !isJsonVariable(name) || value == null || value.rowCount() == 0) {
            return;
        }
        Object raw = value.firstRow().get("value");
        if (!(raw instanceof String text) || text.isBlank()) {
            return;
        }
        try {
            MAPPER.readTree(text);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid " + name, ex);
        }
    }

    private static boolean isJsonVariable(String name) {
        return "triggerJson".equals(name)
                || "inputSchemaJson".equals(name)
                || "outputSchemaJson".equals(name);
    }
}
