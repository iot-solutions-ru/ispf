package com.ispf.ai.http;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolCall;
import com.ispf.ai.LlmToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmHttpSupportToolCallingTest {

    @Test
    void buildsOpenAiToolsAndToolMessages() {
        LlmRequest request = new LlmRequest(
                "test-model",
                List.of(
                        new LlmMessage("user", "list"),
                        new LlmMessage(
                                "assistant",
                                "",
                                null,
                                List.of(new LlmToolCall("call-1", "list_objects", "{\"parent\":\"root\"}")),
                                null
                        ),
                        new LlmMessage("tool", "{\"status\":\"OK\"}", null, List.of(), "call-1")
                ),
                128,
                0.0,
                Map.of(),
                List.of(toolSpec()),
                "auto"
        );

        ObjectNode body = LlmHttpSupport.chatCompletionBody(request);

        ArrayNode tools = (ArrayNode) body.get("tools");
        assertEquals("function", tools.get(0).path("type").asText());
        assertEquals("list_objects", tools.get(0).path("function").path("name").asText());
        assertEquals("auto", body.path("tool_choice").asText());
        ArrayNode messages = (ArrayNode) body.get("messages");
        assertEquals("call-1", messages.get(2).path("tool_call_id").asText());
        assertEquals(
                "{\"parent\":\"root\"}",
                messages.get(1).path("tool_calls").get(0).path("function").path("arguments").asText()
        );
    }

    @Test
    void buildsOllamaToolArgumentsAsObject() {
        LlmRequest request = new LlmRequest(
                "test-model",
                List.of(new LlmMessage(
                        "assistant",
                        "",
                        null,
                        List.of(new LlmToolCall("call-1", "list_objects", "{\"parent\":\"root\"}")),
                        null
                )),
                128,
                0.0,
                Map.of(),
                List.of(toolSpec()),
                "auto"
        );

        ObjectNode body = LlmHttpSupport.ollamaChatBody(request);

        assertFalse(body.path("stream").asBoolean(true));
        assertEquals(
                "root",
                body.path("messages").get(0).path("tool_calls").get(0).path("function").path("arguments").path("parent").asText()
        );
    }

    @Test
    void parsesOpenAiToolCallsWithStringOrObjectArguments() throws Exception {
        String json = """
                {
                  "model":"test-model",
                  "choices":[{"finish_reason":"tool_calls","message":{
                    "content":"",
                    "tool_calls":[
                      {"id":"call-1","type":"function","function":{"name":"list_objects","arguments":"{\\"parent\\":\\"root\\"}"}},
                      {"id":"call-2","type":"function","function":{"name":"finish","arguments":{"summary":"done","result":{}}}}
                    ]
                  }}]
                }
                """;

        LlmResponse response = LlmHttpSupport.parseChatCompletion(json, "fallback");

        assertEquals(2, response.toolCalls().size());
        assertEquals("list_objects", response.toolCalls().get(0).name());
        assertTrue(response.toolCalls().get(1).argumentsJson().contains("\"summary\":\"done\""));
    }

    private static LlmToolSpec toolSpec() {
        return new LlmToolSpec(
                "list_objects",
                "List child objects.",
                Map.of(
                        "type", "object",
                        "properties", Map.of("parent", Map.of("type", "string"))
                )
        );
    }
}
