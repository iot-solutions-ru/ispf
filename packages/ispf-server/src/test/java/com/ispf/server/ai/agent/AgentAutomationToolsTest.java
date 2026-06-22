package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAutomationToolsTest {

    @Mock
    private AutomationTreeService automationTreeService;
    @Mock
    private OperatorAppUiService operatorAppUiService;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;

    private List<PlatformAgentTool> tools;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        tools = AgentAutomationTools.all(
                automationTreeService,
                operatorAppUiService,
                objectManager,
                objectAccessService,
                tenantScopeService,
                new ObjectMapper()
        );
        context = new AgentContext("admin", null, new AgentRunState());
    }

    @Test
    void getAutomationSchemaReturnsDashboardTemplates() throws Exception {
        PlatformAgentTool tool = requireTool("get_automation_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of("topic", "dashboard"), context);
        assertEquals("OK", result.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dashboard = (Map<String, Object>) result.get("dashboard");
        @SuppressWarnings("unchecked")
        List<String> templates = (List<String>) dashboard.get("templates");
        assertTrue(templates.contains("virtual-cluster-overview"));
        assertTrue(templates.contains("virtual-cluster-detail"));
    }

    @Test
    void configureAlertUpdatesExistingRule() throws Exception {
        String path = "root.platform.alert-rules.test-rule";
        PlatformObject node = new PlatformObject("1", path, ObjectType.ALERT, "Test rule", "", null);
        when(objectManager.require(path)).thenReturn(node);
        when(tenantScopeService.isPathVisible(path, null)).thenReturn(true);
        when(automationTreeService.getAlertRule(path)).thenReturn(new AlertRule(
                path,
                "Test rule",
                "root.platform.devices.demo-sensor-01",
                "temperature",
                "self.temperature[\"value\"] > 80",
                "highTemp",
                "",
                true,
                true,
                0,
                false,
                0,
                false,
                null,
                null,
                null,
                null
        ));
        when(automationTreeService.updateAlertRule(
                eq(path),
                eq("Test rule"),
                eq("root.platform.devices.demo-sensor-01"),
                eq("temperature"),
                eq("self.temperature[\"value\"] > 85"),
                eq("highTemp"),
                eq(""),
                eq(true),
                eq(true),
                eq(0),
                eq(false)
        )).thenReturn(new AlertRule(
                path,
                "Test rule",
                "root.platform.devices.demo-sensor-01",
                "temperature",
                "self.temperature[\"value\"] > 85",
                "highTemp",
                "",
                true,
                true,
                0,
                false,
                0,
                false,
                null,
                null,
                null,
                null
        ));

        PlatformAgentTool tool = requireTool("configure_alert");
        Map<String, Object> result = tool.execute(Map.of(
                "path", path,
                "targetObjectPath", "root.platform.devices.demo-sensor-01",
                "watchVariable", "temperature",
                "conditionExpr", "self.temperature[\"value\"] > 85",
                "eventName", "highTemp"
        ), context);

        assertEquals("OK", result.get("status"));
        verify(objectAccessService).requireWrite(path, null);
    }

    @Test
    void listAutomationReturnsEmptyListsWhenNone() throws Exception {
        when(automationTreeService.listAlertRules()).thenReturn(List.of());
        when(automationTreeService.listCorrelators()).thenReturn(List.of());

        PlatformAgentTool tool = requireTool("list_automation");
        Map<String, Object> result = tool.execute(Map.of(), context);

        assertEquals("OK", result.get("status"));
        assertEquals(0, result.get("alertCount"));
        assertEquals(0, result.get("correlatorCount"));
    }

    @Test
    void configureOperatorUiSavesDashboardMenu() throws Exception {
        when(operatorAppUiService.getUi("platform")).thenReturn(Map.of("appId", "platform"));
        when(operatorAppUiService.saveUi(
                eq("platform"),
                eq("Platform HMI"),
                eq("root.platform.dashboards.virt-cluster-overview"),
                any()
        )).thenReturn(Map.of(
                "appId", "platform",
                "defaultDashboard", "root.platform.dashboards.virt-cluster-overview"
        ));

        PlatformAgentTool tool = requireTool("configure_operator_ui");
        Map<String, Object> result = tool.execute(Map.of(
                "appId", "platform",
                "title", "Platform HMI",
                "defaultDashboard", "root.platform.dashboards.virt-cluster-overview",
                "dashboards", List.of(Map.of(
                        "path", "root.platform.dashboards.virt-cluster-overview",
                        "title", "Overview"
                ))
        ), context);

        assertEquals("OK", result.get("status"));
        verify(operatorAppUiService).saveUi(
                eq("platform"),
                eq("Platform HMI"),
                eq("root.platform.dashboards.virt-cluster-overview"),
                any()
        );
    }

    private PlatformAgentTool requireTool(String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }
}
