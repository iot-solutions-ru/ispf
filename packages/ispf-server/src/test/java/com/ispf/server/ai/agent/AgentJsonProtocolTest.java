package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJsonProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesToolActionFromMarkdownWrappedJson() throws Exception {
        String content = """
                Here is my plan:
                ```json
                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices"}}
                ```
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("list_objects", action.toolName());
        assertEquals("root.platform.devices", action.arguments().get("parent"));
    }

    @Test
    void parsesFinishAction() throws Exception {
        String content = """
                {"type":"finish","summary":"Created device lab-sensor-1","result":{"path":"root.platform.devices.lab-sensor-1"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertEquals("Created device lab-sensor-1", action.summary());
        assertEquals("root.platform.devices.lab-sensor-1", action.result().get("path"));
        assertNull(action.toolName());
    }

    @Test
    void acceptsToolAliasField() throws Exception {
        String content = "{\"type\":\"tool\",\"tool\":\"search_context\",\"arguments\":{\"query\":\"DEVICE template\"}}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("search_context", action.toolName());
        assertEquals("DEVICE template", action.arguments().get("query"));
    }

    @Test
    void parsesJsonAfterRedactedThinkingBlock() throws Exception {
        String content = """
                Long reasoning about list_objects...
                </think>

                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("list_objects", action.toolName());
    }

    @Test
    void ignoresNestedObjectTypeInToolArguments() throws Exception {
        String content = """
                Let me create a dashboard.
                {"type":"tool","name":"create_object","arguments":{"parentPath":"root.platform.dashboards","name":"dash","type":"DASHBOARD","templateId":"dashboard-v1"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("create_object", action.toolName());
        assertEquals("DASHBOARD", action.arguments().get("type"));
    }

    @Test
    void parsesJsonEmbeddedInProseAfterThinking() throws Exception {
        String content = """
                I will list devices now.
                </think>

                Here are the devices... Actually:
                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices","lite":true}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_objects", action.toolName());
    }

    @Test
    void parsesFunctionStyleToolCall() throws Exception {
        String content = """
                {"type":"function","name":"add_dashboard_widget","parameters":{"path":"root.platform.dashboards.snmp-host-monitoring","widget":{"id":"if-speed"}}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("add_dashboard_widget", action.toolName());
        assertEquals("root.platform.dashboards.snmp-host-monitoring", action.arguments().get("path"));
    }

    @Test
    void parsesToolWithoutTypeField() throws Exception {
        String content = """
                {"name":"list_variables","arguments":{"path":"root.platform.devices.snmp-localhost"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_variables", action.toolName());
    }

    @Test
    void parsesNestedFunctionObject() throws Exception {
        String content = """
                {"type":"tool","function":{"name":"set_variable","arguments":{"path":"x","name":"layout","value":"{}"}}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("set_variable", action.toolName());
    }

    @Test
    void parsesActionFromJsonArray() throws Exception {
        String content = """
                [{"type":"tool","name":"get_dashboard_layout","arguments":{"path":"root.platform.dashboards.snmp-host-monitoring"}}]
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("get_dashboard_layout", action.toolName());
    }

    @Test
    void parsesActionEmbeddedInMessageWrapper() throws Exception {
        String content = """
                {"type":"message","content":"{\\"type\\":\\"tool\\",\\"name\\":\\"list_variables\\",\\"arguments\\":{\\"path\\":\\"root\\"}}"}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_variables", action.toolName());
    }

    @Test
    void parsesPlainCodeFence() throws Exception {
        String content = """
                ```
                {"type":"finish","summary":"done","result":{}}
                ```
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
    }

    @Test
    void acceptsPlainTextFinishFallback() {
        var action = AgentJsonProtocol.tryParsePlainTextFinish(
                "Сменный отчёт: средняя температура 72.4°C, максимум 81°C за последние 8 часов."
        );
        assertTrue(action.isPresent());
        assertEquals("finish", action.get().type());
        assertTrue(action.get().summary().contains("72.4"));
    }
}
