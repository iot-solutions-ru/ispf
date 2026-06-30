package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentObjectTreeGuideTest {

    @Test
    void referenceTextDocumentsDiscoveryFirst() {
        String text = AgentObjectTreeGuide.referenceText();
        assertTrue(text.contains("list_objects"));
        assertTrue(text.contains("apply_relative_model"));
        assertTrue(text.contains("must come from tool results"));
    }

    @Test
    void summaryIncludesDiscoveryTools() {
        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) AgentObjectTreeGuide.summary().get("discoveryTools");
        assertTrue(tools.contains("list_objects"));
        assertTrue(tools.contains("list_object_models"));
        assertTrue(tools.contains("list_instance_types"));
    }

    @Test
    void summaryListsModelCatalogRoots() {
        @SuppressWarnings("unchecked")
        List<String> catalogs = (List<String>) AgentObjectTreeGuide.summary().get("modelCatalogs");
        assertFalse(catalogs.isEmpty());
        assertTrue(catalogs.stream().anyMatch(path -> path.contains("relative")));
    }

    @Test
    void summaryHasStandardDeviceAndDashboardRoots() {
        Map<String, Object> summary = AgentObjectTreeGuide.summary();
        assertTrue(String.valueOf(summary.get("deviceRoot")).contains("devices"));
        assertTrue(String.valueOf(summary.get("dashboardRoot")).contains("dashboards"));
    }
}
