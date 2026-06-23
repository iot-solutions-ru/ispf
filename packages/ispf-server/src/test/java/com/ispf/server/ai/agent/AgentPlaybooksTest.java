package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlaybooksTest {

    @Test
    void snmpPlaybookFormatsWithoutPlaceholderErrors() {
        String playbook = AgentPlaybooks.snmpLocalhostMonitoring();
        assertTrue(playbook.contains(AgentPlaybooks.SNMP_DASHBOARD_PATH));
        assertTrue(playbook.contains("set_dashboard_layout"));
        assertFalse(playbook.contains("%s"));
    }

    @Test
    void snmpIfMibPlaybookFormatsWithoutPlaceholderErrors() {
        String playbook = AgentPlaybooks.snmpIfMibExtension();
        assertTrue(playbook.contains("ifDescr"));
        assertTrue(playbook.contains(AgentPlaybooks.SNMP_DASHBOARD_PATH));
        assertFalse(playbook.contains("%s"));
    }

    @Test
    void dashboardLayoutEditingIncludesGuide() {
        String playbook = AgentPlaybooks.dashboardLayoutEditing();
        assertTrue(playbook.contains("Дашборды — как работать"));
        assertTrue(playbook.contains("set_dashboard_layout"));
    }
}
