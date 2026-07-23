package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlaybooksTest {

    @Test
    void snmpPlaybookFormatsWithoutPlaceholderErrors() {
        String playbook = AgentPlaybooks.snmpLocalhostMonitoring();
        assertTrue(playbook.contains("search_context"));
        assertTrue(playbook.contains("set_dashboard_layout"));
        assertFalse(playbook.contains("%s"));
    }

    @Test
    void snmpIfMibPlaybookFormatsWithoutPlaceholderErrors() {
        String playbook = AgentPlaybooks.snmpIfMibExtension();
        assertTrue(playbook.contains("ifDescr"));
        assertTrue(playbook.contains("<dashboardPath>"));
        assertFalse(playbook.contains("%s"));
    }

    @Test
    void dashboardLayoutEditingIncludesGuide() {
        String playbook = AgentPlaybooks.dashboardLayoutEditing();
        assertTrue(playbook.contains("Дашборды — как работать"));
        assertTrue(playbook.contains("set_dashboard_layout"));
    }

    @Test
    void groundTruthGuideMentionsDiscoveryTools() {
        String guide = AgentPlaybooks.groundTruthGuide();
        assertTrue(guide.contains("list_objects"));
        assertTrue(guide.contains("list_mixin_blueprints"));
        assertTrue(guide.contains("Object exists"));
    }

    @Test
    void projectBlueprintStartsWithGroundTruthLayer() {
        String guide = AgentPlaybooks.projectBlueprintGuide();
        assertTrue(guide.contains("0. **Ground truth**"));
    }

    @Test
    void virtualPumpStationDoesNotHardcodePumpPaths() {
        String playbook = AgentPlaybooks.virtualPumpStation();
        assertFalse(playbook.contains("name=pump-01"));
        assertFalse(playbook.contains("pump-station type=CUSTOM"));
        assertTrue(playbook.contains("list_objects"));
    }

    @Test
    void endToEndDeployPlaybookFormatsWithoutPlaceholderErrors() {
        String playbook = AgentDeployPlaybook.referenceText();
        assertTrue(playbook.contains("validate_bundle"));
        assertTrue(playbook.contains("import_package"));
        assertFalse(playbook.contains("%s"));
        assertEquals(9, AgentDeployPlaybook.steps().size());
        assertTrue(AgentDeployPlaybook.stepById("validate").tools().contains("validate_bundle"));
    }

    @Test
    void solutionGeneratorPlaybookCoversTreeDashboardsAlerts() {
        String playbook = AgentSolutionGeneratorPlaybook.referenceText();
        assertTrue(playbook.contains("configure_alert"));
        assertTrue(playbook.contains("set_dashboard_layout"));
        assertTrue(playbook.contains("list_variables"));
        assertFalse(playbook.contains("%s"));
    }
}
