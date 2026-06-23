package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWidgetCatalogTest {

    @Test
    void catalogListsAllWidgetTypes() {
        assertEquals(42, AgentWidgetCatalog.all().size());
    }

    @Test
    void catalogResponseFiltersByType() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = AgentWidgetCatalog.catalogResponse("map", "");
        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) result.get("widgets");
        assertEquals("map", widgets.get(0).get("type"));
        assertEquals("parent-catalog", widgets.get(0).get("binding"));
    }

    @Test
    void referenceTextIncludesBindingsAndTemplates() {
        String text = AgentWidgetCatalog.referenceText();
        assertTrue(text.contains("object-variable"));
        assertTrue(text.contains("parent-catalog"));
        assertTrue(text.contains("snmp-host-monitoring"));
        assertTrue(text.contains("`object-table`"));
        assertTrue(text.contains("valueField"));
        assertTrue(text.contains("progress"));
    }
}
