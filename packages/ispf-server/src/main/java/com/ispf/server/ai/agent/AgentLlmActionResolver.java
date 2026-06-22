package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Function;

/**
 * Calls the LLM and parses a tool/finish action with bounded retries and JSON-only nudges.
 */
final class AgentLlmActionResolver {

    private static final String JSON_NUDGE = """
            Your last reply was not a valid agent action. Reply with ONLY one JSON object, no markdown and no explanation:
            {"type":"tool","name":"<tool>","arguments":{...}}
            or {"type":"finish","summary":"...","result":{...}}
            """;

    record ParseAttempt(
            LlmResponse response,
            AgentJsonProtocol.AgentAction action,
            boolean failed,
            String error
    ) {
    }

    private AgentLlmActionResolver() {
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts
    ) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        LlmResponse lastResponse = null;
        String lastError = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            lastResponse = llmProviderRegistry.complete(requestFactory.apply(messages));
            try {
                AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, lastResponse.content());
                return new ParseAttempt(lastResponse, action, false, null);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                if (attempt < attempts - 1) {
                    messages.add(new LlmMessage("assistant", lastResponse.content()));
                    messages.add(new LlmMessage("user", JSON_NUDGE));
                }
            }
        }
        return new ParseAttempt(lastResponse, null, true, lastError);
    }
}
