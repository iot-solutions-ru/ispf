package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void runsTheFirstCanonicalToolWhenTheModelListsSeveral() throws Exception {
        String content = """
                {"type":"tool","name":"create_virtual_device","arguments":{"parentPath":"root.platform.devices","name":"audit-sensor-01"}}
                {"type":"tool","name":"list_variables","arguments":{"path":"root.platform.devices.audit-sensor-01"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("create_virtual_device", action.toolName());
    }

    @Test
    void skipsAProseTemplateBeforeTheRealAction() throws Exception {
        String content = """
                Формат: {"type":"tool","name":"<tool>","arguments":{}}
                {"type":"tool","name":"list_drivers","arguments":{}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_drivers", action.toolName());
    }

    @Test
    void illustrativeJsonFenceInProseDoesNotHideTheTrailingFinish() throws Exception {
        String content = "Пример настройки драйвера:\n```json\n{\"port\": 502}\n```\n"
                + "{\"type\":\"finish\",\"summary\":\"Готово\"}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertEquals("Готово", action.summary());
    }

    @Test
    void jsonFenceInsideSummaryAfterPrefaceIsNotExtracted() throws Exception {
        String content = "Ответ:\n{\"type\":\"finish\",\"summary\":\"Манифест:\\n```json\\n{\\\"appId\\\":\\\"mes\\\"}\\n```\"}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertTrue(action.summary().contains("appId"));
    }

    @Test
    void plainAnswerKeepsItsJsonExample() {
        String content = "Создайте переменную так:\n\n```json\n{\"name\":\"temperature\",\"type\":\"DOUBLE\"}\n```\n\nЗатем привяжите её к виджету.";
        Optional<AgentJsonProtocol.AgentAction> plain = AgentJsonProtocol.tryParsePlainTextFinish(content);
        assertTrue(plain.isPresent());
        assertEquals(content, plain.get().summary());
        assertTrue(AgentJsonProtocol.tryParsePlainTextFinish(
                "Проверю объекты.\n<tool_call>\n{\"name\":\"list_objects\",\"arguments\":{broken}\n</tool_call>").isEmpty());
    }

    @Test
    void salvagedTruncatedFinishUnescapesNewlinesAndUnicode() {
        String content = "{\"type\":\"finish\",\"summary\":\"Шаг 1\\nШаг 2 \\u041f\\u0443\\u043b\\u044c\\u0442 и ещё текст который обрез";
        Optional<AgentJsonProtocol.AgentAction> salvaged =
                AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, content, "ru");
        assertTrue(salvaged.isPresent());
        assertTrue(salvaged.get().summary().startsWith("Шаг 1\nШаг 2 Пульт"), salvaged.get().summary());
    }

    @Test
    void proseBeforeActionKeepsTheStreamedAnswer() {
        String content = "### Создание мнемосхемы\n\n1. Создайте объект MIMIC.\n2. Добавьте элементы.\n"
                + "{\"type\":\"finish\",\"summary\":\"\"}";
        assertEquals("### Создание мнемосхемы\n\n1. Создайте объект MIMIC.\n2. Добавьте элементы.",
                AgentJsonProtocol.proseBeforeAction(content));
        assertNull(AgentJsonProtocol.proseBeforeAction("{\"type\":\"finish\",\"summary\":\"\"}"));
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
    void parsesToolNameInTypeField() throws Exception {
        String content = """
                {"type":"search_context","arguments":{"query":"snmp agent monitoring","topic":"drivers"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("search_context", action.toolName());
        assertEquals("snmp agent monitoring", action.arguments().get("query"));
        assertEquals("drivers", action.arguments().get("topic"));
    }

    @Test
    void takesFirstToolWhenModelDumpsSeveralShorthandActions() throws Exception {
        String content = """
                {"type":"search_context","arguments":{"query":"snmp agent monitoring","topic":"drivers"}}
                {"type":"list_objects","arguments":{"parentPath":"root.platform.devices","lite":true}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("search_context", action.toolName());
        assertEquals("drivers", action.arguments().get("topic"));
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

    @Test
    void salvagesTruncatedPlanFinish() {
        String truncated = """
                {"type":"finish","summary":"План НС","result":{"phase":"plan","interactive":true,"plan":{"goal":"Насосная станция","sections":[{"id":"ground_truth","title":"1. Discovery","summary":"Обнаружение","steps":["list_objects parent=root.platform.devices","get_automation_schema topic=projectBlueprint"]},{"id":"intent_scope","title":"2. Scope","summary":"Цель","steps":["FR mapping"
                """;
        var salvaged = AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, truncated);
        assertTrue(salvaged.isPresent());
        assertEquals("finish", salvaged.get().type());
        assertEquals("План НС", salvaged.get().summary());
        assertTrue(salvaged.get().result().containsKey("plan"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) salvaged.get().result().get("plan");
        assertEquals("Насосная станция", plan.get("goal"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) plan.get("sections");
        assertThat(sections).hasSize(1);
        assertEquals("ground_truth", sections.getFirst().get("id"));
    }

    @Test
    void detectsTruncatedJsonContent() {
        String truncated = """
                {"type":"finish","summary":"Plan","result":{"phase":"plan","plan":{"goal":"MVP","steps":["1. a","2. b
                """;
        assertTrue(AgentJsonProtocol.looksLikeTruncatedContent(truncated));
    }

    @Test
    void completeJsonIsNotMarkedTruncated() throws Exception {
        String content = """
                {"type":"finish","summary":"OK","result":{"phase":"plan","plan":{"goal":"MVP","steps":["1. list_objects"]}}}
                """;
        assertFalse(AgentJsonProtocol.looksLikeTruncatedContent(content));
        AgentJsonProtocol.parse(objectMapper, content);
    }

    @Test
    void parsesFinishWhenSummaryContainsEmbeddedJsonCodeFence() throws Exception {
        String content = "{\"type\":\"finish\",\"summary\":\"## Пример\\n\\n```json\\n{\\\"appId\\\": \\\"mes-reference\\\"}\\n```\","
                + "\"result\":{}}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertTrue(action.summary().contains("mes-reference"));
        assertTrue(action.summary().contains("```json"));
    }

    @Test
    void doesNotTreatManifestInsideFinishSummaryAsAgentAction() throws Exception {
        String content = "{\"type\":\"finish\",\"summary\":\"See ```json\\n{\\\"appId\\\":\\\"mes-reference\\\",\\\"version\\\":\\\"1\\\"}\\n```\","
                + "\"result\":{}}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertTrue(action.result().isEmpty());
    }

    @Test
    void parsesToolActionWithTrailingToolCallCloseTag() throws Exception {
        // Qwen/vLLM sometimes appends </tool_call> after a valid JSON action.
        String content = "{\"type\":\"tool\",\"name\":\"enable_agent_tool_pack\",\"arguments\":{\"pack\":\"devices\"}}\n</tool_call>";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("enable_agent_tool_pack", action.toolName());
        assertEquals("devices", action.arguments().get("pack"));
    }

    @Test
    void parsesToolActionWrappedInToolCallXml() throws Exception {
        String content = "<tool_call>\n{\"type\":\"tool\",\"name\":\"list_objects\",\"arguments\":{\"parent\":\"root.platform.devices\"}}\n</tool_call>";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_objects", action.toolName());
        assertEquals("root.platform.devices", action.arguments().get("parent"));
    }

    @Test
    void coercesToolShapedFinishIntoFinishAction() throws Exception {
        String content = """
                {"type":"tool","name":"finish","arguments":{"summary":"Готово","result":{"phase":"plan","interactive":true}}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertNull(action.toolName());
        assertEquals("Готово", action.summary());
        assertEquals("plan", action.result().get("phase"));
    }

    @Test
    void normalizeActionRemapsFinishToolName() {
        AgentJsonProtocol.AgentAction raw = new AgentJsonProtocol.AgentAction(
                "tool",
                "finish",
                Map.of("summary", "Done", "result", Map.of("ok", true)),
                null,
                null
        );
        AgentJsonProtocol.AgentAction normalized = AgentJsonProtocol.normalizeAction(raw);
        assertEquals("finish", normalized.type());
        assertEquals("Done", normalized.summary());
        assertEquals(true, normalized.result().get("ok"));
    }

    @Test
    void salvagesTruncatedPlanWithoutTypeFinishWhenPhasePresent() {
        String truncated = """
                {"phase":"plan","interactive":true,"plan":{"goal":"SNMP dashboard","sections":[{"id":"ground_truth","title":"Discovery","summary":"list","steps":["list_objects parent=root.platform.dashboards"]},{"id":"build","title":"Build","summary":"create
                """;
        var salvaged = AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, truncated, "ru", true);
        assertTrue(salvaged.isPresent());
        assertEquals("finish", salvaged.get().type());
        assertTrue(Boolean.TRUE.equals(salvaged.get().result().get("planPartial")));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) salvaged.get().result().get("plan");
        assertEquals("SNMP dashboard", plan.get("goal"));
    }

    @Test
    void doesNotStampPlanPhaseWhenSalvagingTruncatedExecutionFinish() {
        String truncated = """
                {"type":"finish","summary":"Dashboard updated on root.platform.dashboards.snmp-host-dashboard with
                """;
        var salvaged = AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, truncated, "en", false);
        assertTrue(salvaged.isPresent());
        assertEquals("finish", salvaged.get().type());
        assertTrue(salvaged.get().result() == null || salvaged.get().result().isEmpty()
                || !salvaged.get().result().containsKey("phase"));
    }

    @Test
    void coercesBarePlanPayloadToFinish() throws Exception {
        String content = """
                {
                  "phase": "plan",
                  "interactive": true,
                  "plan": {
                    "goal": "Create two users and virtual devices"
                  },
                  "summary": "Plan ready"
                }
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertEquals("Plan ready", action.summary());
        assertEquals("plan", action.result().get("phase"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) action.result().get("plan");
        assertEquals("Create two users and virtual devices", plan.get("goal"));
    }
}
