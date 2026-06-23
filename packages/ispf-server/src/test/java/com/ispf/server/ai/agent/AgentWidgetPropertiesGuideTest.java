package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWidgetPropertiesGuideTest {

    @Test
    void allWidgetTypesHavePropertySpecs() {
        assertEquals(AgentWidgetCatalog.all().size(), AgentWidgetPropertiesGuide.allTypeSpecs().size());
    }

    @Test
    void chartSpecRequiresVariableName() {
        Map<String, Object> spec = AgentWidgetPropertiesGuide.propertiesForType("chart");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) spec.get("required");
        assertTrue(required.contains("variableName"));
        assertTrue(String.valueOf(spec.get("notes")).contains("historyEnabled"));
    }

    @Test
    void progressUsesCurrentAndMaxVariables() {
        Map<String, Object> spec = AgentWidgetPropertiesGuide.propertiesForType("progress");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) spec.get("required");
        assertTrue(required.contains("currentVariable"));
        assertTrue(required.contains("maxVariable"));
    }

    @Test
    void catalogResponseIncludesPropertySpecForFilteredType() {
        Map<String, Object> result = AgentWidgetCatalog.catalogResponse("object-table", "");
        assertTrue(result.containsKey("propertySpec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) result.get("propertySpec");
        assertEquals("parent-catalog", spec.get("binding"));
    }

    @Test
    void referenceTextDocumentsValueFieldAndJsonStrings() {
        String text = AgentWidgetPropertiesGuide.referenceText();
        assertTrue(text.contains("valueField"));
        assertTrue(text.contains("columnsJson"));
        assertTrue(text.contains("currentVariable"));
        assertFalse(text.contains("%s"));
    }
}
