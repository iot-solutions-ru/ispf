package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolCall;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLlmActionResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void askProseAnswerIsKeptWithoutASecondModelCall() throws Exception {
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        String prose = "Платформа поддерживает драйверы:\n\n1. **modbus** — опрос ПЛК по Modbus TCP.\n2. **mqtt** — подписка на топики.";
        when(registry.complete(any())).thenReturn(new LlmResponse(prose, "m", null, "stop"));

        AgentLlmActionResolver.ParseAttempt attempt = AgentLlmActionResolver.resolve(
                objectMapper,
                registry,
                new ArrayList<>(List.of(new LlmMessage("user", "Какие драйверы есть?"))),
                messages -> new LlmRequest("m", messages, 100, 0.0),
                3,
                true
        );

        verify(registry, times(1)).complete(any());
        assertThat(attempt.failed()).isFalse();
        assertThat(attempt.action().type()).isEqualTo("finish");
        assertThat(attempt.action().summary()).isEqualTo(prose);
    }

    @Test
    void executeProseStillRetriesForAJsonAction() throws Exception {
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        when(registry.complete(any()))
                .thenReturn(new LlmResponse("Сейчас создам устройство и проверю переменные.", "m", null, "stop"))
                .thenReturn(new LlmResponse(
                        "{\"type\":\"tool\",\"name\":\"list_objects\",\"arguments\":{}}", "m", null, "stop"));

        AgentLlmActionResolver.ParseAttempt attempt = AgentLlmActionResolver.resolve(
                objectMapper,
                registry,
                new ArrayList<>(List.of(new LlmMessage("user", "Создай устройство"))),
                messages -> new LlmRequest("m", messages, 100, 0.0),
                3,
                false
        );

        verify(registry, times(2)).complete(any());
        assertThat(attempt.action().toolName()).isEqualTo("list_objects");
    }

    @Test
    void nativeToolNameThatSwallowedTheJsonTailIsRepaired() {
        LlmToolCall broken = new LlmToolCall(
                "c1",
                "finish\",\"arguments\":{\"summary\":\"Создано устройство audit-exec-01\"}",
                ""
        );
        LlmToolCall repaired = AgentLlmActionResolver.repairNativeToolCall(objectMapper, broken);
        assertThat(repaired.name()).isEqualTo("finish");
        assertThat(repaired.argumentsJson()).contains("audit-exec-01");
    }

    @Test
    void repairedNativeCallReplacesTheGarbageCallInTheEchoedResponse() throws Exception {
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmToolCall broken = new LlmToolCall("c1", "list_objects\",\"arguments\":{\"parent\":\"root\"}", "");
        LlmToolCall extra = new LlmToolCall("c2", "list_drivers", "{}");
        when(registry.complete(any())).thenReturn(new LlmResponse("", "m", null, "tool_calls", List.of(broken, extra)));

        AgentLlmActionResolver.ParseAttempt attempt = AgentLlmActionResolver.resolve(
                objectMapper,
                registry,
                new ArrayList<>(List.of(new LlmMessage("user", "Покажи объекты"))),
                messages -> new LlmRequest("m", messages, 100, 0.0),
                3,
                false
        );

        assertThat(attempt.action().toolName()).isEqualTo("list_objects");
        assertThat(attempt.response().toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.name()).isEqualTo("list_objects"));
    }

    @Test
    void unrecoverableNativeToolNameIsRejected() {
        LlmToolCall broken = new LlmToolCall("c1", "list objects please", "{}");
        assertThatThrownBy(() -> AgentLlmActionResolver.repairNativeToolCall(objectMapper, broken))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
