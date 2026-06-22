package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
